package com.seatlock.dto;

import java.time.Instant;
import java.util.UUID;

public record QueueTicketResponse(
    Long eventId,
    UUID userId,
    String status,
    int position,
    int estimatedWaitSeconds,
    Instant joinedAt
) {}
