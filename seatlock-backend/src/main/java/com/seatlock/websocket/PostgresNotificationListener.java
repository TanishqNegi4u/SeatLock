package com.seatlock.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.seatlock.dto.QueuePositionEvent;
import com.seatlock.dto.SeatUpdateEvent;
import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Listens on PostgreSQL LISTEN/NOTIFY channels using a dedicated, non-pooled
 * JDBC connection (bypasses HikariCP to avoid connection recycling killing LISTEN).
 *
 * On each notification:
 * - Parses the JSON payload
 * - Broadcasts to local STOMP WebSocket clients via SeatWebSocketHandler
 *
 * This solves the "WebSocket state is per-pod" problem:
 * Pod A commits a booking -> pg_notify -> ALL pods' listeners receive it
 * -> each pod broadcasts to its own WebSocket clients.
 */
@Component
public class PostgresNotificationListener implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(PostgresNotificationListener.class);

    private final SeatWebSocketHandler webSocketHandler;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile boolean running = false;
    private volatile Connection connection;

    @Value("${seatlock.listener-datasource.url}")
    private String jdbcUrl;

    @Value("${seatlock.listener-datasource.username}")
    private String username;

    @Value("${seatlock.listener-datasource.password}")
    private String password;

    @Value("${seatlock.websocket.notification-poll-ms:100}")
    private int pollIntervalMs;

    public PostgresNotificationListener(SeatWebSocketHandler webSocketHandler,
                                       ObjectMapper objectMapper) {
        this.webSocketHandler = webSocketHandler;
        this.objectMapper = objectMapper;
    }

    @Override
    public synchronized void start() {
        if (this.running) return;
        this.running = true;
        executor.submit(this::listenLoop);
        log.info("[PG_LISTEN] Starting Postgres notification listener");
    }

    private void listenLoop() {
        while (running) {
            try {
                connectAndListen();
            } catch (Exception e) {
                if (!running) break;
                log.error("[PG_LISTEN] Disconnected. Reconnecting in 5s...", e);
                closeQuietly(connection);
                try {
                    TimeUnit.SECONDS.sleep(5);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void connectAndListen() throws SQLException, InterruptedException {
        // Dedicated unpooled connection — NOT from HikariCP
        this.connection = DriverManager.getConnection(jdbcUrl, username, password);
        PGConnection pgConn = connection.unwrap(PGConnection.class);

        try (Statement stmt = connection.createStatement()) {
            stmt.execute("LISTEN seat_updates");
            stmt.execute("LISTEN waiting_room_updates");
            stmt.execute("LISTEN audit_log_updates");
        }
        log.info("[PG_LISTEN] Connected and listening on seat_updates, waiting_room_updates, audit_log_updates");

        while (running && !connection.isClosed()) {
            // Blocking poll with timeout
            PGNotification[] notifications = pgConn.getNotifications(pollIntervalMs);
            if (notifications != null) {
                for (PGNotification n : notifications) {
                    processNotification(n);
                }
            }
        }
    }

    private void processNotification(PGNotification notification) {
        String channel = notification.getName();
        String payload = notification.getParameter();
        log.debug("[PG_LISTEN] Received on {}: {}", channel, payload);

        try {
            switch (channel) {
                case "seat_updates" -> {
                    SeatUpdateEvent event = objectMapper.readValue(payload, SeatUpdateEvent.class);
                    webSocketHandler.broadcastSeatUpdate(event.eventId(), event);
                }
                case "waiting_room_updates" -> {
                    QueuePositionEvent event = objectMapper.readValue(payload, QueuePositionEvent.class);
                    webSocketHandler.broadcastQueueUpdate(event.eventId(), event);
                }
                case "audit_log_updates" -> {
                    com.seatlock.dto.SeatEventLogDto event = objectMapper.readValue(payload, com.seatlock.dto.SeatEventLogDto.class);
                    webSocketHandler.broadcastAuditEvent(event.eventId(), event);
                }
                default -> log.warn("[PG_LISTEN] Unknown channel: {}", channel);
            }
        } catch (Exception e) {
            log.error("[PG_LISTEN] Failed to process notification on {}: {}", channel, payload, e);
        }
    }

    private void closeQuietly(AutoCloseable c) {
        if (c != null) {
            try { c.close(); } catch (Exception ignored) {}
        }
    }

    @Override
    public synchronized void stop() {
        this.running = false;
        closeQuietly(connection);
        executor.shutdownNow();
        log.info("[PG_LISTEN] Stopped");
    }

    @Override public boolean isRunning() { return running; }
    @Override public int getPhase() { return Integer.MAX_VALUE; }
}
