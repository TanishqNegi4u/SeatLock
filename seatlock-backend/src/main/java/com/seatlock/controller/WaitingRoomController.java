package com.seatlock.controller;

import com.seatlock.config.UserSessionFilter;
import com.seatlock.dto.QueueTicketResponse;
import com.seatlock.service.WaitingRoomService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/events/{eventId}/queue")
public class WaitingRoomController {

    private final WaitingRoomService waitingRoomService;

    public WaitingRoomController(WaitingRoomService waitingRoomService) {
        this.waitingRoomService = waitingRoomService;
    }

    @PostMapping
    public ResponseEntity<QueueTicketResponse> joinQueue(
            @PathVariable Long eventId, HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute(UserSessionFilter.USER_ID_ATTRIBUTE);
        return ResponseEntity.ok(waitingRoomService.joinQueue(eventId, userId));
    }

    @GetMapping("/status")
    public ResponseEntity<QueueTicketResponse> getQueueStatus(
            @PathVariable Long eventId, HttpServletRequest request) {
        UUID userId = (UUID) request.getAttribute(UserSessionFilter.USER_ID_ATTRIBUTE);
        return ResponseEntity.ok(waitingRoomService.getQueueStatus(eventId, userId));
    }
}
