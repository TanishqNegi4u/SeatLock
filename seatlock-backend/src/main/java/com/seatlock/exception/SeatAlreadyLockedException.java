package com.seatlock.exception;

public class SeatAlreadyLockedException extends RuntimeException {
    public SeatAlreadyLockedException(Long seatId) {
        super("Seat is already locked or booked: " + seatId);
    }
}
