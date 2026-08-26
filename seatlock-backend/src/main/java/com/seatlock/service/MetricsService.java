package com.seatlock.service;

import com.seatlock.domain.BookingStatus;
import com.seatlock.domain.SeatStatus;
import com.seatlock.domain.WaitingRoomStatus;
import com.seatlock.dto.AdminMetricsResponse;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.repository.WaitingRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final WaitingRoomRepository waitingRoomRepository;

    // In-memory counters (reset on pod restart — acceptable for MVP)
    private final AtomicLong lockContentionCount = new AtomicLong(0);
    private final AtomicLong totalBookingLatencyMs = new AtomicLong(0);
    private final AtomicLong totalBookings = new AtomicLong(0);

    public MetricsService(SeatRepository seatRepository,
                         BookingRepository bookingRepository,
                         WaitingRoomRepository waitingRoomRepository) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.waitingRoomRepository = waitingRoomRepository;
    }

    /** Get current metrics snapshot for the admin dashboard. */
    @Transactional(readOnly = true)
    public AdminMetricsResponse getMetrics(Long eventId) {
        // Parse seat counts from grouped query
        int totalSeats = 0, available = 0, locked = 0, booked = 0;
        List<Object[]> statusCounts = seatRepository.countByStatusGrouped(eventId);
        for (Object[] row : statusCounts) {
            SeatStatus status = (SeatStatus) row[0];
            int count = ((Number) row[1]).intValue();
            totalSeats += count;
            switch (status) {
                case AVAILABLE -> available = count;
                case LOCKED -> locked = count;
                case BOOKED -> booked = count;
            }
        }

        long confirmedBookings = bookingRepository.countByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
        long waiting = waitingRoomRepository.countByEventIdAndStatus(eventId, WaitingRoomStatus.WAITING);
        long admitted = waitingRoomRepository.countByEventIdAndStatus(eventId, WaitingRoomStatus.ADMITTED);

        long contention = lockContentionCount.get();
        long bookingCount = totalBookings.get();
        double avgLatency = bookingCount > 0
                ? (double) totalBookingLatencyMs.get() / bookingCount
                : 0.0;

        return new AdminMetricsResponse(
                eventId, totalSeats, available, locked, booked,
                confirmedBookings, waiting, admitted,
                contention, avgLatency, Instant.now());
    }

    /** Increment the lock contention counter. */
    public void recordLockContention() {
        lockContentionCount.incrementAndGet();
    }

    /** Record a successful booking's latency. */
    public void recordBookingLatency(long latencyMs) {
        totalBookingLatencyMs.addAndGet(latencyMs);
        totalBookings.incrementAndGet();
    }
}
