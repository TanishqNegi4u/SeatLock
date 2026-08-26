package com.seatlock.scheduler;

import com.seatlock.domain.ActorType;
import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.repository.SeatRepository;
import com.seatlock.service.AuditService;
import com.seatlock.websocket.SeatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Component
public class LockReaperJob {

    private static final Logger log = LoggerFactory.getLogger(LockReaperJob.class);
    private static final long REAPER_ADVISORY_LOCK_ID = 1001L;

    private final SeatRepository seatRepository;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final SeatWebSocketHandler webSocketHandler;

    @Value("${seatlock.lock-ttl-minutes:5}")
    private int lockTtlMinutes;

    public LockReaperJob(SeatRepository seatRepository,
                        AuditService auditService,
                        JdbcTemplate jdbcTemplate,
                        SeatWebSocketHandler webSocketHandler) {
        this.seatRepository = seatRepository;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
        this.webSocketHandler = webSocketHandler;
    }

    @Scheduled(fixedDelayString = "${seatlock.reaper-interval-ms:30000}")
    @Transactional
    public void reapExpiredLocks() {
        org.slf4j.MDC.put("traceId", "reaper-" + java.util.UUID.randomUUID().toString().substring(0, 8));
        try {
            Boolean acquired = jdbcTemplate.queryForObject(
                    "SELECT pg_try_advisory_xact_lock(?)", Boolean.class, REAPER_ADVISORY_LOCK_ID);

            if (Boolean.FALSE.equals(acquired)) {
                log.debug("[REAPER] Advisory lock held by another instance — skipping");
                return;
            }

            log.debug("[REAPER] Acquired advisory lock — scanning for expired locks");

            Instant cutoff = Instant.now().minus(lockTtlMinutes, ChronoUnit.MINUTES);
            List<Seat> expired = seatRepository.findExpiredLocks(cutoff);

            if (expired.isEmpty()) {
                log.debug("[REAPER] No expired locks found");
                return;
            }

            log.info("[REAPER] Found {} expired lock(s) — releasing", expired.size());

            for (Seat seat : expired) {
                String label = seat.getLabel();
                String lockedByShort = seat.getLockedBy() != null
                        ? seat.getLockedBy().toString().substring(0, 8) : "unknown";

                seat.setStatus(SeatStatus.AVAILABLE);
                seat.setLockedBy(null);
                seat.setLockedAt(null);
                seatRepository.save(seat);

                auditService.logSeatEvent(
                        seat.getId(), seat.getEventId(),
                        SeatStatus.LOCKED.name(), SeatStatus.AVAILABLE.name(),
                        null, ActorType.REAPER,
                        "Lock expired (TTL=" + lockTtlMinutes + "min, was locked by " + lockedByShort + ")");

                // Notify all pods: seat is back to AVAILABLE
                webSocketHandler.notifySeatUpdate(seat);

                log.info("[REAPER] Released seat {} (was locked by user {})", label, lockedByShort);
            }

            log.info("[REAPER] Released {} expired lock(s)", expired.size());
        } finally {
            org.slf4j.MDC.remove("traceId");
        }
    }
}
