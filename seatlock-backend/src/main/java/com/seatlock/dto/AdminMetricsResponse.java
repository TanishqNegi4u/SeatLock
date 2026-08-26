package com.seatlock.dto;

import java.time.Instant;

public record AdminMetricsResponse(
    Long eventId,
    int totalSeats,
    int availableSeats,
    int lockedSeats,
    int bookedSeats,
    long totalBookings,
    long waitingInQueue,
    long admittedFromQueue,
    long lockContentionCount,
    double avgBookingLatencyMs,
    String podHostname,
    Instant timestamp
) {}
