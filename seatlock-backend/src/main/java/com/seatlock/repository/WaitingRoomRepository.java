package com.seatlock.repository;

import com.seatlock.domain.WaitingRoomEntry;
import com.seatlock.domain.WaitingRoomStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface WaitingRoomRepository extends JpaRepository<WaitingRoomEntry, Long> {
    Optional<WaitingRoomEntry> findByEventIdAndUserId(Long eventId, UUID userId);

    long countByEventIdAndStatus(Long eventId, WaitingRoomStatus status);

    // Get the next position number for a new entry
    @Query("SELECT COALESCE(MAX(w.position), 0) + 1 FROM WaitingRoomEntry w WHERE w.eventId = :eventId")
    int getNextPosition(@Param("eventId") Long eventId);
}
