package com.seatlock.repository;

import com.seatlock.domain.BookingRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface BookingRequestRepository extends JpaRepository<BookingRequest, Long> {
    Optional<BookingRequest> findByIdempotencyKey(UUID idempotencyKey);
}
