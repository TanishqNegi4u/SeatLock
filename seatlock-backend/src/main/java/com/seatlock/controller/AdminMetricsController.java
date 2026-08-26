package com.seatlock.controller;

import com.seatlock.domain.SeatEventLog;
import com.seatlock.dto.AdminMetricsResponse;
import com.seatlock.dto.MetricsSnapshotDto;
import com.seatlock.dto.SeatEventLogDto;
import com.seatlock.repository.SeatEventLogRepository;
import com.seatlock.service.MetricsHistoryService;
import com.seatlock.service.MetricsService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;

@RestController
public class AdminMetricsController {

    private final MetricsService metricsService;
    private final MetricsHistoryService metricsHistoryService;
    private final SeatEventLogRepository seatEventLogRepository;

    public AdminMetricsController(MetricsService metricsService,
                                  MetricsHistoryService metricsHistoryService,
                                  SeatEventLogRepository seatEventLogRepository) {
        this.metricsService = metricsService;
        this.metricsHistoryService = metricsHistoryService;
        this.seatEventLogRepository = seatEventLogRepository;
    }

    @GetMapping("/api/admin/metrics")
    public ResponseEntity<AdminMetricsResponse> getMetrics(
            @RequestParam(defaultValue = "1") Long eventId) {
        return ResponseEntity.ok(metricsService.getMetrics(eventId));
    }

    @GetMapping({"/api/events/{eventId}/audit-log", "/api/admin/events/{eventId}/audit-log"})
    public ResponseEntity<List<SeatEventLogDto>> getAuditLog(
            @PathVariable Long eventId,
            @RequestParam(defaultValue = "50") int limit) {

        int cappedLimit = Math.min(Math.max(limit, 1), 100);
        List<SeatEventLog> logs = seatEventLogRepository.findByEventIdOrderByCreatedAtDesc(
                eventId, PageRequest.of(0, cappedLimit));

        List<SeatEventLogDto> dtos = logs.stream()
                .map(l -> new SeatEventLogDto(
                        l.getId(),
                        l.getSeatId(),
                        l.getEventId(),
                        l.getFromStatus(),
                        l.getToStatus(),
                        l.getActorUserId(),
                        l.getActorType() != null ? l.getActorType().name() : "SYSTEM",
                        l.getReason(),
                        l.getPodHostname() != null ? l.getPodHostname() : "local-pod",
                        l.getCreatedAt() != null ? l.getCreatedAt() : Instant.now()
                ))
                .toList();

        return ResponseEntity.ok(dtos);
    }

    @GetMapping({"/api/events/{eventId}/metrics-history", "/api/admin/events/{eventId}/metrics-history"})
    public ResponseEntity<List<MetricsSnapshotDto>> getMetricsHistory(
            @PathVariable Long eventId) {
        return ResponseEntity.ok(metricsHistoryService.getHistory(eventId));
    }
}
