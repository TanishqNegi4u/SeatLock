package com.seatlock.service;

import com.seatlock.domain.ActorType;
import com.seatlock.domain.SeatEventLog;
import com.seatlock.dto.SeatEventLogDto;
import com.seatlock.repository.SeatEventLogRepository;
import com.seatlock.websocket.SeatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final SeatEventLogRepository seatEventLogRepository;
    private final SeatWebSocketHandler webSocketHandler;

    public AuditService(SeatEventLogRepository seatEventLogRepository,
                        @Lazy SeatWebSocketHandler webSocketHandler) {
        this.seatEventLogRepository = seatEventLogRepository;
        this.webSocketHandler = webSocketHandler;
    }

    /**
     * Record a seat status transition in the audit log.
     * Uses MANDATORY propagation — must be called within an existing transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void logSeatEvent(Long seatId, Long eventId, String fromStatus, String toStatus,
                            UUID actorUserId, ActorType actorType, String reason) {
        String podHostname = System.getenv("HOSTNAME") != null
                ? System.getenv("HOSTNAME")
                : "local-pod";

        SeatEventLog entry = SeatEventLog.builder()
                .seatId(seatId)
                .eventId(eventId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorUserId(actorUserId)
                .actorType(actorType)
                .reason(reason)
                .podHostname(podHostname)
                .build();

        entry = seatEventLogRepository.save(entry);

        SeatEventLogDto dto = new SeatEventLogDto(
                entry.getId(),
                seatId,
                eventId,
                fromStatus,
                toStatus,
                actorUserId,
                actorType != null ? actorType.name() : "SYSTEM",
                reason,
                podHostname,
                entry.getCreatedAt() != null ? entry.getCreatedAt() : Instant.now()
        );

        // Push live over WebSocket STOMP channel via pg_notify
        if (webSocketHandler != null) {
            webSocketHandler.notifyAuditEvent(dto);
        }

        log.info("[AUDIT] seat={} {} -> {} by {} ({}) pod={} reason='{}'",
                seatId, fromStatus, toStatus, actorType,
                actorUserId != null ? actorUserId.toString().substring(0, 8) : "system",
                podHostname,
                reason);
    }
}
