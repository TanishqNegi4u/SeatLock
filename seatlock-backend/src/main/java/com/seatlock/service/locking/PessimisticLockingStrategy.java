package com.seatlock.service.locking;

import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.exception.BookingConflictException;
import com.seatlock.exception.SeatNotFoundException;
import com.seatlock.repository.SeatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Pessimistic locking via SELECT ... FOR UPDATE SKIP LOCKED.
 *
 * How it works:
 * - tryLockSeat: acquires a Postgres row-level lock on the seat row.
 *   If another transaction holds the lock, SKIP LOCKED returns zero rows
 *   instead of blocking — the caller gets an instant "seat unavailable".
 * - confirmBooking/releaseLock: use FOR UPDATE (without SKIP LOCKED)
 *   to prevent TOCTOU races with the reaper — we WANT to block briefly
 *   if the reaper is mid-release, rather than skip.
 */
@Service
public class PessimisticLockingStrategy implements LockingStrategy {

    private static final Logger log = LoggerFactory.getLogger(PessimisticLockingStrategy.class);
    private final SeatRepository seatRepository;

    public PessimisticLockingStrategy(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Override
    @Transactional
    public Optional<Seat> tryLockSeat(Long seatId, UUID userId) {
        log.debug("[PESSIMISTIC] Attempting to lock seat {} for user {}",
                seatId, userId.toString().substring(0, 8));

        // SELECT ... FOR UPDATE SKIP LOCKED
        // Returns empty if: seat doesn't exist, not AVAILABLE, or row-locked by another txn
        Optional<Seat> maybeSeat = seatRepository.findAvailableForUpdate(seatId);

        if (maybeSeat.isEmpty()) {
            log.info("[PESSIMISTIC] Seat {} not available (locked or taken)", seatId);
            return Optional.empty();
        }

        Seat seat = maybeSeat.get();
        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedBy(userId);
        seat.setLockedAt(Instant.now());
        Seat saved = seatRepository.saveAndFlush(seat);

        log.info("[PESSIMISTIC] Seat {} locked by user {}",
                saved.getLabel(), userId.toString().substring(0, 8));
        return Optional.of(saved);
    }

    @Override
    @Transactional
    public Seat confirmBooking(Long seatId, UUID userId) {
        // FOR UPDATE (blocking) to prevent race with reaper
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        if (seat.getStatus() != SeatStatus.LOCKED) {
            throw new BookingConflictException(
                    "Seat " + seat.getLabel() + " is " + seat.getStatus() + ", not LOCKED. " +
                    "Lock may have expired and been released by the reaper.");
        }

        if (!userId.equals(seat.getLockedBy())) {
            throw new BookingConflictException(
                    "Seat " + seat.getLabel() + " is locked by another user");
        }

        seat.setStatus(SeatStatus.BOOKED);
        seat.setLockedBy(null);
        seat.setLockedAt(null);
        Seat saved = seatRepository.saveAndFlush(seat);

        log.info("[PESSIMISTIC] Seat {} BOOKED by user {}",
                saved.getLabel(), userId.toString().substring(0, 8));
        return saved;
    }

    @Override
    @Transactional
    public void releaseLock(Long seatId, UUID userId) {
        // FOR UPDATE (blocking) to prevent race with reaper
        Seat seat = seatRepository.findByIdForUpdate(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        if (seat.getStatus() != SeatStatus.LOCKED) {
            log.debug("[PESSIMISTIC] Seat {} is {} — nothing to release",
                    seat.getLabel(), seat.getStatus());
            return; // Idempotent: already released or booked
        }

        if (!userId.equals(seat.getLockedBy())) {
            throw new BookingConflictException(
                    "Seat " + seat.getLabel() + " is locked by another user — cannot release");
        }

        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setLockedBy(null);
        seat.setLockedAt(null);
        seatRepository.saveAndFlush(seat);

        log.info("[PESSIMISTIC] Seat {} released by user {}",
                seat.getLabel(), userId.toString().substring(0, 8));
    }
}
