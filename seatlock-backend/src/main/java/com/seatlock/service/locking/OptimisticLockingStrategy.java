package com.seatlock.service.locking;

import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.exception.BookingConflictException;
import com.seatlock.exception.SeatNotFoundException;
import com.seatlock.repository.SeatRepository;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Optimistic locking via JPA @Version column.
 *
 * How it works:
 * - No row-level locks are acquired during the transaction.
 * - On saveAndFlush(), Hibernate emits UPDATE ... WHERE id=? AND version=?.
 *   If another transaction incremented the version first, zero rows are updated
 *   and Spring throws ObjectOptimisticLockingFailureException.
 * - The caller gets an instant "seat just got taken" response — fast-fail, no wait.
 *
 * Trade-off vs pessimistic:
 * - Higher throughput under moderate contention (no DB locks held).
 * - Higher failure/retry rate under extreme contention (version conflicts).
 * - UX: user sees "seat taken, pick another" instead of waiting briefly.
 */
@Service
public class OptimisticLockingStrategy implements LockingStrategy {

    private static final Logger log = LoggerFactory.getLogger(OptimisticLockingStrategy.class);
    private final SeatRepository seatRepository;

    public OptimisticLockingStrategy(SeatRepository seatRepository) {
        this.seatRepository = seatRepository;
    }

    @Override
    @Transactional
    public Optional<Seat> tryLockSeat(Long seatId, UUID userId) {
        log.debug("[OPTIMISTIC] Attempting to lock seat {} for user {}",
                seatId, userId.toString().substring(0, 8));

        Seat seat = seatRepository.findById(seatId).orElse(null);
        if (seat == null || seat.getStatus() != SeatStatus.AVAILABLE) {
            log.info("[OPTIMISTIC] Seat {} not available", seatId);
            return Optional.empty();
        }

        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedBy(userId);
        seat.setLockedAt(Instant.now());

        try {
            Seat saved = seatRepository.saveAndFlush(seat);
            log.info("[OPTIMISTIC] Seat {} locked by user {}",
                    saved.getLabel(), userId.toString().substring(0, 8));
            return Optional.of(saved);
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            // Another transaction incremented the version first — this user lost the race
            log.info("[OPTIMISTIC] Version conflict on seat {} — another user won the race", seatId);
            return Optional.empty();
        }
    }

    @Override
    @Transactional
    public Seat confirmBooking(Long seatId, UUID userId) {
        Seat seat = seatRepository.findById(seatId)
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

        try {
            Seat saved = seatRepository.saveAndFlush(seat);
            log.info("[OPTIMISTIC] Seat {} BOOKED by user {}",
                    saved.getLabel(), userId.toString().substring(0, 8));
            return saved;
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            throw new BookingConflictException(
                    "Seat " + seat.getLabel() + " was modified concurrently — booking failed");
        }
    }

    @Override
    @Transactional
    public void releaseLock(Long seatId, UUID userId) {
        Seat seat = seatRepository.findById(seatId)
                .orElseThrow(() -> new SeatNotFoundException(seatId));

        if (seat.getStatus() != SeatStatus.LOCKED) {
            log.debug("[OPTIMISTIC] Seat {} is {} — nothing to release",
                    seat.getLabel(), seat.getStatus());
            return; // Idempotent
        }
        if (!userId.equals(seat.getLockedBy())) {
            throw new BookingConflictException(
                    "Seat " + seat.getLabel() + " is locked by another user — cannot release");
        }

        seat.setStatus(SeatStatus.AVAILABLE);
        seat.setLockedBy(null);
        seat.setLockedAt(null);

        try {
            seatRepository.saveAndFlush(seat);
            log.info("[OPTIMISTIC] Seat {} released by user {}",
                    seat.getLabel(), userId.toString().substring(0, 8));
        } catch (ObjectOptimisticLockingFailureException | OptimisticLockException e) {
            log.warn("[OPTIMISTIC] Version conflict releasing seat {} — likely reaper beat us", seatId);
            // Reaper already released it — safe to swallow
        }
    }
}
