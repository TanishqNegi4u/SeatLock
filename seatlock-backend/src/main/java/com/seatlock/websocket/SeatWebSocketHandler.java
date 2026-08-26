package com.seatlock.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.domain.Seat;
import com.seatlock.dto.QueuePositionEvent;
import com.seatlock.dto.SeatUpdateEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Centralized handler for seat and queue WebSocket notifications.
 *
 * Outgoing path (called by services within their transaction):
 *   service -> notifySeatUpdate() -> pg_notify('seat_updates', JSON)
 *   The notification fires ONLY on commit (Postgres transactional NOTIFY semantics).
 *
 * Incoming path (called by PostgresNotificationListener):
 *   pg LISTEN -> broadcastSeatUpdate() -> SimpMessagingTemplate -> STOMP clients
 */
@Component
public class SeatWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(SeatWebSocketHandler.class);
    private final SimpMessagingTemplate messagingTemplate;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public SeatWebSocketHandler(SimpMessagingTemplate messagingTemplate,
                               JdbcTemplate jdbcTemplate,
                               ObjectMapper objectMapper) {
        this.messagingTemplate = messagingTemplate;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    // ── Outgoing: fire pg_notify (called within a @Transactional context) ──

    /**
     * Send a seat update notification via Postgres NOTIFY.
     * Must be called within an existing transaction (fires on commit).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifySeatUpdate(Seat seat) {
        SeatUpdateEvent event = new SeatUpdateEvent(
                seat.getId(), seat.getEventId(), seat.getSectionName(),
                seat.getRowNumber(), seat.getSeatNumber(),
                seat.getStatus().name(), seat.getLockedBy(), seat.getLabel());
        pgNotify("seat_updates", event);
    }

    /**
     * Send a queue position notification via Postgres NOTIFY.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void notifyQueueUpdate(QueuePositionEvent event) {
        pgNotify("waiting_room_updates", event);
    }

    // ── Incoming: broadcast to local STOMP clients (called by listener) ──

    /**
     * Broadcast a seat update to all subscribers of the event's seat topic.
     */
    public void broadcastSeatUpdate(Long eventId, SeatUpdateEvent event) {
        messagingTemplate.convertAndSend("/topic/event/" + eventId + "/seats", event);
        log.debug("[WS] Broadcast seat update: {} -> {}", event.label(), event.status());
    }

    /**
     * Send a queue position update to a specific user.
     */
    public void broadcastQueueUpdate(Long eventId, QueuePositionEvent event) {
        messagingTemplate.convertAndSend(
                "/topic/event/" + eventId + "/queue/" + event.userId(), event);
        log.debug("[WS] Broadcast queue update: user={} status={}", event.userId(), event.status());
    }

    // ── Internal ──

    private void pgNotify(String channel, Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            // Escape single quotes for SQL string literal
            String escaped = json.replace("'", "''");
            jdbcTemplate.execute("SELECT pg_notify('" + channel + "', '" + escaped + "')");
            log.debug("[PG_NOTIFY] channel={} payload_length={}", channel, json.length());
        } catch (JsonProcessingException e) {
            log.error("[PG_NOTIFY] Failed to serialize payload for channel {}", channel, e);
        }
    }
}
