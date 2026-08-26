package com.seatlock.service;

import com.seatlock.domain.Booking;
import com.seatlock.domain.BookingStatus;
import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.CreateBookingRequest;
import com.seatlock.exception.BookingConflictException;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.BookingRequestRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.locking.LockingStrategy;
import com.seatlock.websocket.SeatWebSocketHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class BookingServiceUnitTest {

    @Mock
    private LockingStrategy lockingStrategy;

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private BookingRequestRepository bookingRequestRepository;

    @Mock
    private SeatRepository seatRepository;

    @Mock
    private PaymentService paymentService;

    @Mock
    private AuditService auditService;

    @Mock
    private MetricsService metricsService;

    @Mock
    private SeatWebSocketHandler webSocketHandler;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private BookingService bookingService;

    private final Long eventId = 1L;
    private final Long seatId = 100L;
    private final UUID idempotencyKey = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private CreateBookingRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateBookingRequest(seatId, idempotencyKey);
    }

    @Test
    void testSuccessfulBookingFlow() {
        when(jdbcTemplate.update(anyString(), eq(idempotencyKey), eq(seatId), eq(eventId), eq(userId)))
                .thenReturn(1);
        when(paymentService.processPayment(eq(userId), eq(seatId), anyString())).thenReturn(true);

        Seat bookedSeat = Seat.builder()
                .id(seatId)
                .eventId(eventId)
                .sectionName("A")
                .rowNumber(1)
                .seatNumber(1)
                .status(SeatStatus.BOOKED)
                .build();
        when(lockingStrategy.confirmBooking(seatId, userId)).thenReturn(bookedSeat);

        Booking savedBooking = Booking.builder()
                .id(501L)
                .seatId(seatId)
                .eventId(eventId)
                .userId(userId)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(idempotencyKey)
                .confirmedAt(Instant.now())
                .build();
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);

        BookingResponse response = bookingService.bookSeat(eventId, request, userId);

        assertNotNull(response);
        assertEquals("CONFIRMED", response.status());
        assertEquals(501L, response.bookingId());
        verify(paymentService).processPayment(eq(userId), eq(seatId), anyString());
        verify(lockingStrategy).confirmBooking(seatId, userId);
        verify(bookingRepository).save(any(Booking.class));
        verify(webSocketHandler).notifySeatUpdate(bookedSeat);
    }

    @Test
    void testPaymentFailureCompensation() {
        when(jdbcTemplate.update(anyString(), eq(idempotencyKey), eq(seatId), eq(eventId), eq(userId)))
                .thenReturn(1);
        when(paymentService.processPayment(eq(userId), eq(seatId), anyString())).thenReturn(false);
        when(seatRepository.findById(seatId)).thenReturn(Optional.of(Seat.builder().id(seatId).eventId(eventId).status(SeatStatus.AVAILABLE).build()));

        BookingResponse response = bookingService.bookSeat(eventId, request, userId);

        assertEquals("FAILED", response.status());
        verify(lockingStrategy).releaseLock(seatId, userId);
        verify(lockingStrategy, never()).confirmBooking(any(), any());
        verify(bookingRepository, never()).save(any());
    }

    @Test
    void testIdempotencyDuplicateRequest() {
        when(jdbcTemplate.update(anyString(), eq(idempotencyKey), eq(seatId), eq(eventId), eq(userId)))
                .thenReturn(0); // 0 rows updated -> conflict detected

        Booking existingBooking = Booking.builder()
                .id(501L)
                .seatId(seatId)
                .eventId(eventId)
                .userId(userId)
                .status(BookingStatus.CONFIRMED)
                .idempotencyKey(idempotencyKey)
                .confirmedAt(Instant.now())
                .build();
        when(bookingRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingBooking));

        BookingResponse response = bookingService.bookSeat(eventId, request, userId);

        assertEquals("DUPLICATE", response.status());
        assertEquals(501L, response.bookingId());
        verify(paymentService, never()).processPayment(any(), any(), any());
    }

    @Test
    void testLockExpirationDuringPayment() {
        when(jdbcTemplate.update(anyString(), eq(idempotencyKey), eq(seatId), eq(eventId), eq(userId)))
                .thenReturn(1);
        when(paymentService.processPayment(eq(userId), eq(seatId), anyString())).thenReturn(true);
        when(lockingStrategy.confirmBooking(seatId, userId)).thenThrow(new BookingConflictException("Lock expired"));

        BookingResponse response = bookingService.bookSeat(eventId, request, userId);

        assertEquals("FAILED", response.status());
        assertTrue(response.message().contains("expired"));
    }
}
