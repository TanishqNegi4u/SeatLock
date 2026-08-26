package com.seatlock.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBookingRequest(
    @NotNull Long seatId,
    @NotNull UUID idempotencyKey
) {}
