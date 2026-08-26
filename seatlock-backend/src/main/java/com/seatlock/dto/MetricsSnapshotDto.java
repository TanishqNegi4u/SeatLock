package com.seatlock.dto;

import java.time.Instant;

public record MetricsSnapshotDto(
    Instant timestamp,
    long lockContentionCount,
    double avgBookingLatencyMs,
    int availableSeats,
    int lockedSeats,
    int bookedSeats,
    String podHostname
) {}
