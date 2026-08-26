package com.seatlock.repository;

import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SeatRepository extends JpaRepository<Seat, Long> {
    List<Seat> findByEventId(Long eventId);

    List<Seat> findByEventIdAndStatus(Long eventId, SeatStatus status);

    // Pessimistic locking: acquire row lock on an AVAILABLE seat, skip if already locked by another txn.
    // Returns empty if seat doesn't exist, isn't AVAILABLE, or is row-locked by another transaction.
    @Query(value = """
        SELECT * FROM seats
        WHERE id = :seatId AND status = 'AVAILABLE'
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    Optional<Seat> findAvailableForUpdate(@Param("seatId") Long seatId);

    // Row-lock a seat by ID regardless of status — used by confirmBooking/releaseLock
    // to prevent TOCTOU races with the reaper.
    @Query(value = "SELECT * FROM seats WHERE id = :seatId FOR UPDATE", nativeQuery = true)
    Optional<Seat> findByIdForUpdate(@Param("seatId") Long seatId);

    // Reaper: find seats with expired locks
    @Query("SELECT s FROM Seat s WHERE s.status = com.seatlock.domain.SeatStatus.LOCKED AND s.lockedAt < :cutoff")
    List<Seat> findExpiredLocks(@Param("cutoff") Instant cutoff);

    // Count seats by status for admin metrics
    @Query("SELECT s.status, COUNT(s) FROM Seat s WHERE s.eventId = :eventId GROUP BY s.status")
    List<Object[]> countByStatusGrouped(@Param("eventId") Long eventId);
}
