package com.seatlock.service;

import com.seatlock.dto.AdminMetricsResponse;
import com.seatlock.dto.MetricsSnapshotDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Maintains an in-memory ring buffer of the last 60 telemetry snapshots (every 5 seconds)
 * to feed real-time line charts in the Admin Dashboard without heavy DB historical queries.
 */
@Service
public class MetricsHistoryService {

    private static final Logger log = LoggerFactory.getLogger(MetricsHistoryService.class);
    private static final int MAX_HISTORY_POINTS = 60;

    private final MetricsService metricsService;
    private final ConcurrentLinkedDeque<MetricsSnapshotDto> history = new ConcurrentLinkedDeque<>();

    public MetricsHistoryService(MetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @Scheduled(fixedRate = 5000)
    public void captureSnapshot() {
        try {
            AdminMetricsResponse metrics = metricsService.getMetrics(1L);
            MetricsSnapshotDto snapshot = new MetricsSnapshotDto(
                    Instant.now(),
                    metrics.lockContentionCount(),
                    metrics.avgBookingLatencyMs(),
                    metrics.availableSeats(),
                    metrics.lockedSeats(),
                    metrics.bookedSeats(),
                    metrics.podHostname()
            );

            history.addLast(snapshot);
            while (history.size() > MAX_HISTORY_POINTS) {
                history.pollFirst();
            }
        } catch (Exception e) {
            log.trace("[METRICS-HISTORY] Error capturing snapshot: {}", e.getMessage());
        }
    }

    public List<MetricsSnapshotDto> getHistory(Long eventId) {
        if (history.isEmpty()) {
            captureSnapshot();
        }
        return Collections.unmodifiableList(new ArrayList<>(history));
    }
}
