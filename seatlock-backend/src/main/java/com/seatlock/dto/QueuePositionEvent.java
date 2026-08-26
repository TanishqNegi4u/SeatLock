package com.seatlock.dto;

import java.util.UUID;

public record QueuePositionEvent(
    Long eventId,
    UUID userId,
    String status,
    int position,
    int estimatedWaitSeconds,
    int queueLength
) {}
