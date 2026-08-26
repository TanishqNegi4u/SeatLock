package com.seatlock.dto;

import java.util.UUID;

public record SeatUpdateEvent(
    Long seatId,
    Long eventId,
    String sectionName,
    int rowNumber,
    int seatNumber,
    String status,
    UUID lockedBy,
    String label
) {}
