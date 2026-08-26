package com.seatlock.integration;

import com.seatlock.domain.ActorType;
import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatEventLog;
import com.seatlock.domain.SeatStatus;
import com.seatlock.repository.SeatEventLogRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.scheduler.LockReaperJob;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class LockReaperIntegrationTest {

    @Autowired
    private LockReaperJob lockReaperJob;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatEventLogRepository seatEventLogRepository;

    @Test
    public void testReapExpiredLocks() {
        Long seatId = 3L;
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedBy(UUID.randomUUID());
        seat.setLockedAt(Instant.now().minus(10, ChronoUnit.MINUTES));
        seatRepository.saveAndFlush(seat);

        lockReaperJob.reapExpiredLocks();

        Seat updatedSeat = seatRepository.findById(seatId).orElseThrow();
        assertEquals(SeatStatus.AVAILABLE, updatedSeat.getStatus());
        assertNull(updatedSeat.getLockedBy());
        assertNull(updatedSeat.getLockedAt());

        List<SeatEventLog> logs = seatEventLogRepository.findBySeatIdOrderByCreatedAtDesc(seatId);
        boolean reaperLogFound = logs.stream()
                .anyMatch(log -> "AVAILABLE".equals(log.getToStatus()) && ActorType.REAPER == log.getActorType());
        assertTrue(reaperLogFound, "Seat event log should contain a transition by REAPER");
    }
}
