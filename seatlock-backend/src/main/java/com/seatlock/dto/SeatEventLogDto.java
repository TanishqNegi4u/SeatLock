package com.seatlock.dto;

import java.time.Instant;
import java.util.UUID;

public record SeatEventLogDto(
    Long id,
    Long seatId,
    Long eventId,
    String fromStatus,
    String toStatus,
    UUID actorUserId,
    String actorType,
    String reason,
    String podHostname,
    Instant createdAt
) {}
