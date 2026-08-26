package com.seatlock.integration;

import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.CreateBookingRequest;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.BookingService;
import com.seatlock.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
public class ConcurrentIdempotencyTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private PaymentService paymentService;

    @Test
    @DisplayName("20 simultaneous threads with identical idempotencyKey produce exactly 1 confirmed booking and 19 duplicates")
    public void testConcurrentDuplicateRequests() throws Exception {
        Long eventId = 1L;
        Long seatId = 5L;
        UUID userId = UUID.randomUUID();
        UUID sharedIdempotencyKey = UUID.randomUUID();

        // Prepare seat in LOCKED state for this user
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedBy(userId);
        seat.setLockedAt(Instant.now());
        seatRepository.saveAndFlush(seat);

        when(paymentService.processPayment(any(), anyLong(), any())).thenReturn(true);

        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        List<BookingResponse> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // wait for gun
                    CreateBookingRequest request = new CreateBookingRequest(seatId, sharedIdempotencyKey);
                    BookingResponse response = bookingService.bookSeat(eventId, request, userId);
                    responses.add(response);
                } catch (Exception e) {
                    e.printStackTrace();
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Fire all 20 threads simultaneously
        startGate.countDown();
        endGate.await();
        executor.shutdown();

        assertEquals(threadCount, responses.size(), "All 20 requests should complete");

        long confirmedCount = responses.stream().filter(r -> "CONFIRMED".equals(r.status())).count();
        long duplicateCount = responses.stream().filter(r -> "DUPLICATE".equals(r.status())).count();

        // Exactly 1 must win the initial insert; the other 19 must return DUPLICATE
        assertEquals(1, confirmedCount, "Exactly 1 request must be CONFIRMED");
        assertEquals(threadCount - 1, duplicateCount, "All remaining 19 requests must return DUPLICATE");

        // Verify all 20 responses return the exact same non-null booking ID
        Long primaryBookingId = responses.get(0).bookingId();
        assertNotNull(primaryBookingId);
        for (BookingResponse resp : responses) {
            assertEquals(primaryBookingId, resp.bookingId(), "All responses must point to the identical bookingId");
        }

        // Verify database contains exactly 1 booking row for this idempotency key
        assertEquals(1, bookingRepository.findByIdempotencyKey(sharedIdempotencyKey).stream().count());
    }
}
