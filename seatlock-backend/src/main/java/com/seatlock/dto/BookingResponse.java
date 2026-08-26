package com.seatlock.dto;

import java.time.Instant;
import java.util.UUID;

public record BookingResponse(
    Long bookingId,
    Long seatId,
    String seatLabel,
    String status,
    UUID idempotencyKey,
    Instant confirmedAt,
    String message
) {
    public static BookingResponse success(Long bookingId, Long seatId, String seatLabel, UUID idempotencyKey, Instant confirmedAt) {
        return new BookingResponse(bookingId, seatId, seatLabel, "CONFIRMED", idempotencyKey, confirmedAt, "Booking confirmed");
    }

    public static BookingResponse conflict(String message) {
        return new BookingResponse(null, null, null, "FAILED", null, null, message);
    }

    public static BookingResponse duplicate(Long bookingId, Long seatId, String seatLabel, UUID idempotencyKey, Instant confirmedAt) {
        return new BookingResponse(bookingId, seatId, seatLabel, "DUPLICATE", idempotencyKey, confirmedAt, "Duplicate request — returning existing booking");
    }
}
