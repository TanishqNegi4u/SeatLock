package com.seatlock.exception;

public class SeatNotFoundException extends RuntimeException {
    public SeatNotFoundException(Long seatId) {
        super("Seat not found: " + seatId);
    }
}
