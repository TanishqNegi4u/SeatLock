package com.seatlock.repository;

import com.seatlock.domain.Booking;
import com.seatlock.domain.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    Optional<Booking> findByIdempotencyKey(UUID idempotencyKey);
    List<Booking> findByEventId(Long eventId);
    long countByEventIdAndStatus(Long eventId, BookingStatus status);
}
