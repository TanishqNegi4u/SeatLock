package com.seatlock.concurrency;

import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.locking.OptimisticLockingStrategy;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "seatlock.locking-strategy=OPTIMISTIC")
public class OptimisticLockingConcurrencyTest {

    @Autowired
    private OptimisticLockingStrategy optimisticLockingStrategy;

    @Autowired
    private SeatRepository seatRepository;

    @Test
    public void testOptimisticLockingConcurrency() throws InterruptedException {
        Long seatId = 2L; // Seeded seat

        int threadCount = 50;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                UUID userId = UUID.randomUUID();
                try {
                    latch.await();
                    Optional<Seat> locked = optimisticLockingStrategy.tryLockSeat(seatId, userId);
                    if (locked.isPresent()) {
                        successCount.incrementAndGet();
                    } else {
                        failureCount.incrementAndGet();
                    }
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

        assertEquals(1, successCount.get(), "Exactly one thread should acquire lock via @Version");
        assertEquals(49, failureCount.get(), "49 threads should encounter optimistic locking conflict");

        Seat updatedSeat = seatRepository.findById(seatId).orElseThrow();
        assertEquals(SeatStatus.LOCKED, updatedSeat.getStatus());
    }
}
