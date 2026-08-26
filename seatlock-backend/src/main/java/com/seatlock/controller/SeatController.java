package com.seatlock.controller;

import com.seatlock.config.UserSessionFilter;
import com.seatlock.dto.SeatDto;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.service.SeatLockService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/seats")
public class SeatController {

    private final SeatLockService seatLockService;

    public SeatController(SeatLockService seatLockService) {
        this.seatLockService = seatLockService;
    }

    @GetMapping
    public ResponseEntity<SeatMapResponse> getSeatMap(@PathVariable Long eventId) {
        return ResponseEntity.ok(seatLockService.getSeatMap(eventId));
    }

    @PostMapping("/{seatId}/lock")
    public ResponseEntity<SeatDto> lockSeat(@PathVariable Long eventId,
                                           @PathVariable Long seatId,
                                           HttpServletRequest request) {
        UUID userId = getUserId(request);
        SeatDto locked = seatLockService.lockSeat(eventId, seatId, userId);
        return ResponseEntity.ok(locked);
    }

    @DeleteMapping("/{seatId}/lock")
    public ResponseEntity<Void> releaseLock(@PathVariable Long eventId,
                                           @PathVariable Long seatId,
                                           HttpServletRequest request) {
        UUID userId = getUserId(request);
        seatLockService.releaseSeatLock(eventId, seatId, userId);
        return ResponseEntity.noContent().build();
    }

    private UUID getUserId(HttpServletRequest request) {
        return (UUID) request.getAttribute(UserSessionFilter.USER_ID_ATTRIBUTE);
    }
}
