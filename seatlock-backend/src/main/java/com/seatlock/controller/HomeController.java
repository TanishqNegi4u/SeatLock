package com.seatlock.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HomeController {

    @GetMapping("/")
    public ResponseEntity<Map<String, Object>> index() {
        return ResponseEntity.ok(Map.of(
                "application", "SeatLock Concurrency Engine",
                "status", "RUNNING",
                "frontend_ui", "http://localhost:3000",
                "seat_map_api", "/api/events/1/seats",
                "actuator_health", "/actuator/health",
                "admin_metrics", "/api/admin/metrics?eventId=1"
        ));
    }
}
