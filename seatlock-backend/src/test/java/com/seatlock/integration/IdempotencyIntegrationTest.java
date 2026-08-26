package com.seatlock.integration;

import com.seatlock.domain.BookingStatus;
import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.CreateBookingRequest;
import com.seatlock.repository.BookingRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.BookingService;
import com.seatlock.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@SpringBootTest
@ActiveProfiles("test")
public class IdempotencyIntegrationTest {

    @Autowired
    private BookingService bookingService;

    @Autowired
    private SeatRepository seatRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @MockBean
    private PaymentService paymentService;

    @Test
    public void testIdempotency() {
        Long eventId = 1L;
        Long seatId = 4L;
        UUID userId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();

        // Prepare seat in LOCKED state for this user
        Seat seat = seatRepository.findById(seatId).orElseThrow();
        seat.setStatus(SeatStatus.LOCKED);
        seat.setLockedBy(userId);
        seat.setLockedAt(Instant.now());
        seatRepository.saveAndFlush(seat);

        when(paymentService.processPayment(any(), anyLong(), any())).thenReturn(true);

        CreateBookingRequest request = new CreateBookingRequest(seatId, idempotencyKey);

        // First request
        BookingResponse response1 = bookingService.bookSeat(eventId, request, userId);
        assertEquals("CONFIRMED", response1.status());
        assertNotNull(response1.bookingId());

        // Second identical request (retry / duplicate)
        BookingResponse response2 = bookingService.bookSeat(eventId, request, userId);
        assertEquals("DUPLICATE", response2.status());
        assertEquals(response1.bookingId(), response2.bookingId());

        assertTrue(bookingRepository.findByIdempotencyKey(idempotencyKey).isPresent());
        assertEquals(1, bookingRepository.findByIdempotencyKey(idempotencyKey).stream().count());

        verify(paymentService, times(1)).processPayment(any(), anyLong(), any());
    }
}
