package com.seatlock.service;

import com.seatlock.domain.BookingStatus;
import com.seatlock.domain.SeatStatus;
import com.seatlock.domain.WaitingRoomStatus;
import com.seatlock.dto.AdminMetricsResponse;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.repository.WaitingRoomRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class MetricsService {

    private static final Logger log = LoggerFactory.getLogger(MetricsService.class);

    private final SeatRepository seatRepository;
    private final BookingRepository bookingRepository;
    private final WaitingRoomRepository waitingRoomRepository;

    // In-memory atomic counters for AdminMetricsResponse
    private final AtomicLong lockContentionCount = new AtomicLong(0);
    private final AtomicLong totalBookingLatencyMs = new AtomicLong(0);
    private final AtomicLong totalBookings = new AtomicLong(0);

    // Micrometer metrics for Prometheus scraping
    private final Counter lockContentionCounter;
    private final Counter totalBookingsCounter;
    private final Timer bookingLatencyTimer;

    private final AtomicInteger gaugeAvailableSeats = new AtomicInteger(0);
    private final AtomicInteger gaugeLockedSeats = new AtomicInteger(0);
    private final AtomicInteger gaugeBookedSeats = new AtomicInteger(0);

    public MetricsService(SeatRepository seatRepository,
                          BookingRepository bookingRepository,
                          WaitingRoomRepository waitingRoomRepository,
                          MeterRegistry meterRegistry) {
        this.seatRepository = seatRepository;
        this.bookingRepository = bookingRepository;
        this.waitingRoomRepository = waitingRoomRepository;

        this.lockContentionCounter = Counter.builder("seatlock.lock.contention.total")
                .description("Total seat lock contention events")
                .register(meterRegistry);

        this.totalBookingsCounter = Counter.builder("seatlock.bookings.total")
                .description("Total confirmed bookings")
                .register(meterRegistry);

        this.bookingLatencyTimer = Timer.builder("seatlock.booking.latency")
                .description("Seat booking transaction latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        Gauge.builder("seatlock.seats.available", gaugeAvailableSeats, AtomicInteger::get)
                .description("Currently available seats")
                .register(meterRegistry);

        Gauge.builder("seatlock.seats.locked", gaugeLockedSeats, AtomicInteger::get)
                .description("Currently locked seats")
                .register(meterRegistry);

        Gauge.builder("seatlock.seats.booked", gaugeBookedSeats, AtomicInteger::get)
                .description("Currently booked seats")
                .register(meterRegistry);
    }

    /** Get current metrics snapshot for the admin dashboard. */
    @Transactional(readOnly = true)
    public AdminMetricsResponse getMetrics(Long eventId) {
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

        // Update gauges for Prometheus
        gaugeAvailableSeats.set(available);
        gaugeLockedSeats.set(locked);
        gaugeBookedSeats.set(booked);

        long confirmedBookings = bookingRepository.countByEventIdAndStatus(eventId, BookingStatus.CONFIRMED);
        long waiting = waitingRoomRepository.countByEventIdAndStatus(eventId, WaitingRoomStatus.WAITING);
        long admitted = waitingRoomRepository.countByEventIdAndStatus(eventId, WaitingRoomStatus.ADMITTED);

        long contention = lockContentionCount.get();
        long bookingCount = totalBookings.get();
        double avgLatency = bookingCount > 0
                ? (double) totalBookingLatencyMs.get() / bookingCount
                : 0.0;

        String podHostname = System.getenv("HOSTNAME") != null
                ? System.getenv("HOSTNAME")
                : "local-pod";

        return new AdminMetricsResponse(
                eventId, totalSeats, available, locked, booked,
                confirmedBookings, waiting, admitted,
                contention, avgLatency, podHostname, Instant.now());
    }

    /** Increment the lock contention counter. */
    public void recordLockContention() {
        lockContentionCount.incrementAndGet();
        lockContentionCounter.increment();
    }

    /** Record a successful booking's latency. */
    public void recordBookingLatency(long latencyMs) {
        totalBookingLatencyMs.addAndGet(latencyMs);
        totalBookings.incrementAndGet();
        totalBookingsCounter.increment();
        bookingLatencyTimer.record(Duration.ofMillis(latencyMs));
    }
}
