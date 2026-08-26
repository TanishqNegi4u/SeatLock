package com.seatlock.service;

import com.seatlock.domain.WaitingRoomEntry;
import com.seatlock.domain.WaitingRoomStatus;
import com.seatlock.dto.QueueTicketResponse;
import com.seatlock.repository.WaitingRoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class WaitingRoomService {

    private static final Logger log = LoggerFactory.getLogger(WaitingRoomService.class);
    private final WaitingRoomRepository waitingRoomRepository;

    @Value("${seatlock.waiting-room.admission-batch-size:50}")
    private int admissionBatchSize;

    @Value("${seatlock.waiting-room.admission-interval-ms:5000}")
    private int admissionIntervalMs;

    public WaitingRoomService(WaitingRoomRepository waitingRoomRepository) {
        this.waitingRoomRepository = waitingRoomRepository;
    }

    /** Add a user to the waiting room for an event. Idempotent via UNIQUE(event_id, user_id). */
    @Transactional
    public QueueTicketResponse joinQueue(Long eventId, UUID userId) {
        // Check if already in queue
        var existing = waitingRoomRepository.findByEventIdAndUserId(eventId, userId);
        if (existing.isPresent()) {
            log.debug("[QUEUE] User {} already in queue for event {}", userId.toString().substring(0, 8), eventId);
            return toTicket(existing.get());
        }

        int position = waitingRoomRepository.getNextPosition(eventId);

        WaitingRoomEntry entry = WaitingRoomEntry.builder()
                .eventId(eventId)
                .userId(userId)
                .position(position)
                .status(WaitingRoomStatus.WAITING)
                .build();

        try {
            entry = waitingRoomRepository.saveAndFlush(entry);
        } catch (DataIntegrityViolationException e) {
            // Race: another request inserted first — return existing
            entry = waitingRoomRepository.findByEventIdAndUserId(eventId, userId)
                    .orElseThrow(() -> new RuntimeException("Queue join failed unexpectedly"));
        }

        log.info("[QUEUE] User {} joined queue for event {} at position {}",
                userId.toString().substring(0, 8), eventId, entry.getPosition());
        return toTicket(entry);
    }

    /** Get the current queue status for a user. */
    @Transactional(readOnly = true)
    public QueueTicketResponse getQueueStatus(Long eventId, UUID userId) {
        WaitingRoomEntry entry = waitingRoomRepository.findByEventIdAndUserId(eventId, userId)
                .orElseThrow(() -> new RuntimeException("User not in queue for event " + eventId));
        return toTicket(entry);
    }

    /** Check if a user has been admitted from the waiting room. */
    @Transactional(readOnly = true)
    public boolean isUserAdmitted(Long eventId, UUID userId) {
        return waitingRoomRepository.findByEventIdAndUserId(eventId, userId)
                .map(e -> e.getStatus() == WaitingRoomStatus.ADMITTED)
                .orElse(false);
    }

    private QueueTicketResponse toTicket(WaitingRoomEntry entry) {
        int estimatedWait = 0;
        if (entry.getStatus() == WaitingRoomStatus.WAITING) {
            // Rough ETA: (position / batchSize) * intervalMs / 1000
            long waitingAhead = waitingRoomRepository.countByEventIdAndStatus(
                    entry.getEventId(), WaitingRoomStatus.WAITING);
            int cyclesNeeded = (int) Math.ceil((double) waitingAhead / admissionBatchSize);
            estimatedWait = cyclesNeeded * (admissionIntervalMs / 1000);
        }

        return new QueueTicketResponse(
                entry.getEventId(),
                entry.getUserId(),
                entry.getStatus().name(),
                entry.getPosition() != null ? entry.getPosition() : 0,
                estimatedWait,
                entry.getJoinedAt());
    }
}
