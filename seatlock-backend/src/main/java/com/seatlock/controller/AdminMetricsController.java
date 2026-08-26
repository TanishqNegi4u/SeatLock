package com.seatlock.controller;

import com.seatlock.dto.AdminMetricsResponse;
import com.seatlock.service.MetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminMetricsController {

    private final MetricsService metricsService;

    public AdminMetricsController(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<AdminMetricsResponse> getMetrics(
            @RequestParam(defaultValue = "1") Long eventId) {
        return ResponseEntity.ok(metricsService.getMetrics(eventId));
    }
}
