package com.seatlock.controller;

import com.seatlock.config.UserSessionFilter;
import com.seatlock.dto.BookingResponse;
import com.seatlock.dto.CreateBookingRequest;
import com.seatlock.service.BookingService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}")
public class BookingController {

    private final BookingService bookingService;

    public BookingController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @PostMapping("/book")
    public ResponseEntity<BookingResponse> bookSeat(
            @PathVariable Long eventId,
            @Valid @RequestBody CreateBookingRequest request,
            HttpServletRequest httpRequest) {
        UUID userId = (UUID) httpRequest.getAttribute(UserSessionFilter.USER_ID_ATTRIBUTE);
        BookingResponse response = bookingService.bookSeat(eventId, request, userId);

        return switch (response.status()) {
            case "CONFIRMED", "DUPLICATE" -> ResponseEntity.ok(response);
            default -> ResponseEntity.status(409).body(response);
        };
    }
}
