package com.seatlock.service;

import com.seatlock.domain.ActorType;
import com.seatlock.domain.SeatEventLog;
import com.seatlock.repository.SeatEventLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuditService {

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);
    private final SeatEventLogRepository seatEventLogRepository;

    public AuditService(SeatEventLogRepository seatEventLogRepository) {
        this.seatEventLogRepository = seatEventLogRepository;
    }

    /**
     * Record a seat status transition in the audit log.
     * Uses MANDATORY propagation — must be called within an existing transaction.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void logSeatEvent(Long seatId, Long eventId, String fromStatus, String toStatus,
                            UUID actorUserId, ActorType actorType, String reason) {
        SeatEventLog entry = SeatEventLog.builder()
                .seatId(seatId)
                .eventId(eventId)
                .fromStatus(fromStatus)
                .toStatus(toStatus)
                .actorUserId(actorUserId)
                .actorType(actorType)
                .reason(reason)
                .build();

        seatEventLogRepository.save(entry);

        log.info("[AUDIT] seat={} {} -> {} by {} ({}) reason='{}'",
                seatId, fromStatus, toStatus, actorType,
                actorUserId != null ? actorUserId.toString().substring(0, 8) : "system",
                reason);
    }
}
