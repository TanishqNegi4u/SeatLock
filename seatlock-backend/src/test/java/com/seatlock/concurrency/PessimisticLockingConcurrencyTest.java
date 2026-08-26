package com.seatlock.concurrency;

import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatEventLog;
import com.seatlock.domain.SeatStatus;
import com.seatlock.dto.SeatDto;
import com.seatlock.exception.SeatAlreadyLockedException;
import com.seatlock.repository.SeatEventLogRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.SeatLockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class PessimisticLockingConcurrencyTest {

    @Autowired
    private SeatLockService seatLockService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private SeatEventLogRepository seatEventLogRepository;

    @Test
    public void testPessimisticLockingConcurrency() throws InterruptedException {
        Long eventId = 1L;
        Long seatId = 1L; // Seeded by Flyway migration

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        UUID[] winningUserId = new UUID[1];

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                UUID userId = UUID.randomUUID();
                try {
                    latch.await();
                    SeatDto locked = seatLockService.lockSeat(eventId, seatId, userId);
                    if (locked != null) {
                        successCount.incrementAndGet();
                        winningUserId[0] = userId;
                    }
                } catch (SeatAlreadyLockedException e) {
                    failureCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        latch.countDown();
        doneLatch.await();
        executorService.shutdown();

        assertEquals(1, successCount.get(), "Exactly one thread should successfully lock the seat");
        assertEquals(49, failureCount.get(), "49 threads should fail to lock");

        Seat updatedSeat = seatRepository.findById(seatId).orElseThrow();
        assertEquals(SeatStatus.LOCKED, updatedSeat.getStatus());
        assertEquals(winningUserId[0], updatedSeat.getLockedBy());

        List<SeatEventLog> logs = seatEventLogRepository.findBySeatIdOrderByCreatedAtDesc(seatId);
        long lockEvents = logs.stream().filter(l -> "LOCKED".equals(l.getToStatus())).count();
        assertTrue(lockEvents >= 1, "Audit log must contain lock event");
    }
}
