package com.seatlock.repository;

import com.seatlock.domain.SeatEventLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SeatEventLogRepository extends JpaRepository<SeatEventLog, Long> {
    List<SeatEventLog> findBySeatIdOrderByCreatedAtDesc(Long seatId);
    List<SeatEventLog> findByEventIdOrderByCreatedAtDesc(Long eventId);
}
