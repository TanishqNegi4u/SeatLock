package com.seatlock.dto;

import java.util.UUID;

public record SeatDto(
    Long id,
    String sectionName,
    int rowNumber,
    int seatNumber,
    String status,
    UUID lockedBy,
    String label
) {}
