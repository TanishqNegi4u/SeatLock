package com.seatlock.service;

import com.seatlock.domain.*;
import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.CreateBookingRequest;
import com.seatlock.exception.BookingConflictException;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.BookingRequestRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.locking.LockingStrategy;
import com.seatlock.websocket.SeatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final LockingStrategy lockingStrategy;
    private final BookingRepository bookingRepository;
    private final BookingRequestRepository bookingRequestRepository;
    private final SeatRepository seatRepository;
    private final PaymentService paymentService;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SeatWebSocketHandler webSocketHandler;

    private final JdbcTemplate jdbcTemplate;

    public BookingService(LockingStrategy lockingStrategy,
                         BookingRepository bookingRepository,
                         BookingRequestRepository bookingRequestRepository,
                         SeatRepository seatRepository,
                         PaymentService paymentService,
                         AuditService auditService,
                         MetricsService metricsService,
                         SeatWebSocketHandler webSocketHandler,
                         JdbcTemplate jdbcTemplate) {
        this.lockingStrategy = lockingStrategy;
        this.bookingRepository = bookingRepository;
        this.bookingRequestRepository = bookingRequestRepository;
        this.seatRepository = seatRepository;
        this.paymentService = paymentService;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.webSocketHandler = webSocketHandler;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Full booking flow: idempotency check -> payment -> confirm/compensate.
     * Expects the seat to already be LOCKED by this user (via SeatLockService.lockSeat).
     */
    @Transactional
    public BookingResponse bookSeat(Long eventId, CreateBookingRequest request, UUID userId) {
        long startTime = System.currentTimeMillis();

        // ── Step 1: Idempotency gate (PostgreSQL atomic ON CONFLICT) ────
        int rows = jdbcTemplate.update("""
                INSERT INTO booking_requests (idempotency_key, seat_id, event_id, user_id, status, created_at)
                VALUES (?, ?, ?, ?, 'PENDING', now())
                ON CONFLICT (idempotency_key) DO NOTHING
                """, request.idempotencyKey(), request.seatId(), eventId, userId);

        if (rows == 0) {
            log.info("[BOOKING] Duplicate idempotency key {} — returning existing result",
                    request.idempotencyKey().toString().substring(0, 8));
            return handleDuplicateRequest(request.idempotencyKey());
        }

        // ── Step 2: Process mock payment ──────────────────────────────────
        boolean paymentSuccess = paymentService.processPayment(
                userId, request.seatId(), request.idempotencyKey().toString());

        if (paymentSuccess) {
            // ── Step 3a: Payment succeeded — confirm booking ──────────────
            try {
                Seat bookedSeat = lockingStrategy.confirmBooking(request.seatId(), userId);

                Booking booking = Booking.builder()
                        .seatId(request.seatId())
                        .eventId(eventId)
                        .userId(userId)
                        .status(BookingStatus.CONFIRMED)
                        .idempotencyKey(request.idempotencyKey())
                        .confirmedAt(Instant.now())
                        .build();
                booking = bookingRepository.save(booking);

                updateBookingRequestStatus(request.idempotencyKey(), BookingRequestStatus.COMPLETED);

                auditService.logSeatEvent(request.seatId(), eventId,
                        SeatStatus.LOCKED.name(), SeatStatus.BOOKED.name(),
                        userId, ActorType.USER, "Payment successful — booking confirmed");

                // Notify all pods: seat is now BOOKED
                webSocketHandler.notifySeatUpdate(bookedSeat);

                long latency = System.currentTimeMillis() - startTime;
                metricsService.recordBookingLatency(latency);
                log.info("[BOOKING] SUCCESS seat={} user={} booking={} latency={}ms",
                        bookedSeat.getLabel(), userId.toString().substring(0, 8),
                        booking.getId(), latency);

                return BookingResponse.success(
                        booking.getId(), request.seatId(), bookedSeat.getLabel(),
                        request.idempotencyKey(), booking.getConfirmedAt());

            } catch (BookingConflictException e) {
                updateBookingRequestStatus(request.idempotencyKey(), BookingRequestStatus.FAILED);
                metricsService.recordLockContention();
                log.warn("[BOOKING] Lock expired during payment for seat={}: {}",
                        request.seatId(), e.getMessage());
                return BookingResponse.conflict(
                        "Your seat lock expired during payment. Please select a new seat.");
            }
        } else {
            // ── Step 3b: Payment failed — compensate ─────────────────────
            try {
                lockingStrategy.releaseLock(request.seatId(), userId);
                auditService.logSeatEvent(request.seatId(), eventId,
                        SeatStatus.LOCKED.name(), SeatStatus.AVAILABLE.name(),
                        userId, ActorType.USER, "Payment failed — seat released (compensation)");

                // Notify all pods: seat is back to AVAILABLE
                seatRepository.findById(request.seatId())
                        .ifPresent(webSocketHandler::notifySeatUpdate);

            } catch (Exception e) {
                log.warn("[BOOKING] Could not release lock after payment failure for seat={}: {}",
                        request.seatId(), e.getMessage());
            }

            updateBookingRequestStatus(request.idempotencyKey(), BookingRequestStatus.FAILED);
            metricsService.recordLockContention();

            log.info("[BOOKING] FAILED (payment) seat={} user={}",
                    request.seatId(), userId.toString().substring(0, 8));
            return BookingResponse.conflict("Payment failed. Your seat has been released.");
        }
    }

    private BookingResponse handleDuplicateRequest(UUID idempotencyKey) {
        return bookingRepository.findByIdempotencyKey(idempotencyKey)
                .map(booking -> BookingResponse.duplicate(
                        booking.getId(), booking.getSeatId(), "(see original)",
                        idempotencyKey, booking.getConfirmedAt()))
                .orElse(BookingResponse.conflict(
                        "Duplicate request — original is still processing or failed"));
    }

    private void updateBookingRequestStatus(UUID idempotencyKey, BookingRequestStatus status) {
        bookingRequestRepository.findByIdempotencyKey(idempotencyKey)
                .ifPresent(br -> {
                    br.setStatus(status);
                    bookingRequestRepository.save(br);
                });
    }
}
