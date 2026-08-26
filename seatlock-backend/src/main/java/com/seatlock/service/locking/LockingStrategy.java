package com.seatlock.service.locking;

import com.seatlock.domain.Seat;
import java.util.Optional;
import java.util.UUID;

/**
 * Strategy interface for seat locking. Implementations provide either
 * pessimistic (SELECT FOR UPDATE SKIP LOCKED) or optimistic (@Version)
 * concurrency control.
 */
public interface LockingStrategy {

    /**
     * Attempt to lock a seat for the given user.
     * @return the locked Seat if successful, empty if the seat is unavailable
     */
    Optional<Seat> tryLockSeat(Long seatId, UUID userId);

    /**
     * Confirm a locked seat as booked.
     * @return the booked Seat
     */
    Seat confirmBooking(Long seatId, UUID userId);

    /**
     * Release a lock on a seat (user cancellation or payment failure).
     */
    void releaseLock(Long seatId, UUID userId);
}
