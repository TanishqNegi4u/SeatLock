package com.seatlock.controller;

import com.seatlock.domain.Event;
import com.seatlock.repository.EventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listEvents() {
        List<Map<String, Object>> events = eventRepository.findAll().stream()
                .map(e -> Map.<String, Object>of(
                        "id", e.getId(),
                        "name", e.getName(),
                        "eventDate", e.getEventDate().toString(),
                        "status", e.getStatus().name()
                ))
                .toList();
        return ResponseEntity.ok(events);
    }

    @GetMapping("/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));
        return ResponseEntity.ok(Map.of(
                "id", event.getId(),
                "name", event.getName(),
                "eventDate", event.getEventDate().toString(),
                "status", event.getStatus().name()
        ));
    }
}
