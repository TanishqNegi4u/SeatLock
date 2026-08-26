package com.seatlock.dto;

import java.util.List;
import java.util.Map;

public record SeatMapResponse(
    Long eventId,
    String eventName,
    int totalSeats,
    int availableSeats,
    int lockedSeats,
    int bookedSeats,
    Map<String, List<SeatDto>> sectionSeats  // key = section name ("A", "B", ...)
) {}
