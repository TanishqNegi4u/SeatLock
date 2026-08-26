package com.seatlock.scheduler;

import com.seatlock.dto.QueuePositionEvent;
import com.seatlock.repository.WaitingRoomRepository;
import com.seatlock.websocket.SeatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Periodically admits a batch of users from the waiting room.
 * Uses pg_try_advisory_xact_lock for single-pod execution and
 * atomic UPDATE...RETURNING to prevent double-admission.
 */
@Component
public class WaitingRoomAdmissionJob {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoomAdmissionJob.class);
    private static final long ADMISSION_ADVISORY_LOCK_ID = 1002L;

    private final WaitingRoomRepository waitingRoomRepository;
    private final JdbcTemplate jdbcTemplate;
    private final SeatWebSocketHandler webSocketHandler;

    @Value("${seatlock.waiting-room.admission-batch-size:50}")
    private int batchSize;

    public WaitingRoomAdmissionJob(WaitingRoomRepository waitingRoomRepository,
                                  JdbcTemplate jdbcTemplate,
                                  SeatWebSocketHandler webSocketHandler) {
        this.waitingRoomRepository = waitingRoomRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelayString = "${seatlock.waiting-room.admission-interval-ms:5000}")
    @Transactional
    public void admitNextBatch() {
        org.slf4j.MDC.put("traceId", "admission-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        try {
            // ── Gate: only one pod runs at a time ─────────────────────────
            Boolean acquired = jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(?)",
                    Boolean.class,
                    ADMISSION_ADVISORY_LOCK_ID);

            if (Boolean.FALSE.equals(acquired)) {
                log.debug("[ADMISSION] Advisory lock held by another instance — skipping");
                return;
            }

            // ── Atomic UPDATE...RETURNING ─────────────────────────────────
            // Admits the next batch of WAITING users, ordered by position.
            // FOR UPDATE SKIP LOCKED prevents double-admission if advisory lock fails.
            List<Map<String, Object>> admitted = jdbcTemplate.queryForList("""
                    UPDATE waiting_room_entries
                    SET status = 'ADMITTED', admitted_at = now()
                    WHERE id IN (
                        SELECT id FROM waiting_room_entries
                        WHERE status = 'WAITING'
                        ORDER BY position
                        LIMIT ?
                        FOR UPDATE SKIP LOCKED
                    )
                    RETURNING id, event_id, user_id::text, position
                    """, batchSize);

            if (admitted.isEmpty()) {
                log.debug("[ADMISSION] No users waiting for admission");
                return;
            }

            log.info("[ADMISSION] Admitted {} user(s)", admitted.size());

            // ── Notify each admitted user via pg_notify ───────────────────
            for (Map<String, Object> row : admitted) {
                Long eventId = ((Number) row.get("event_id")).longValue();
                UUID userId = UUID.fromString((String) row.get("user_id"));
                int position = ((Number) row.get("position")).intValue();

                QueuePositionEvent event = new QueuePositionEvent(
                    eventId, userId, "ADMITTED", position, 0, 0);
                webSocketHandler.notifyQueueUpdate(event);

                log.info("[ADMISSION] Admitted user {} at position {} for event {}",
                        userId.toString().substring(0, 8), position, eventId);
            }
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }
}
