package com.seatlock.service;

import com.seatlock.domain.ActorType;
import com.seatlock.domain.Event;
import com.seatlock.domain.Seat;
import com.seatlock.domain.SeatStatus;
import com.seatlock.dto.SeatDto;
import com.seatlock.dto.SeatMapResponse;
import com.seatlock.exception.SeatAlreadyLockedException;
import com.seatlock.repository.EventRepository;
import com.seatlock.repository.SeatRepository;
import com.seatlock.repository.SectionRepository;
import com.seatlock.service.locking.LockingStrategy;
import com.seatlock.websocket.SeatWebSocketHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class SeatLockService {

    private static final Logger log = LoggerFactory.getLogger(SeatLockService.class);

    private final SeatRepository seatRepository;
    private final SectionRepository sectionRepository;
    private final EventRepository eventRepository;
    private final LockingStrategy lockingStrategy;
    private final AuditService auditService;
    private final MetricsService metricsService;
    private final SeatWebSocketHandler webSocketHandler;

    public SeatLockService(SeatRepository seatRepository,
                          SectionRepository sectionRepository,
                          EventRepository eventRepository,
                          LockingStrategy lockingStrategy,
                          AuditService auditService,
                          MetricsService metricsService,
                          SeatWebSocketHandler webSocketHandler) {
        this.seatRepository = seatRepository;
        this.sectionRepository = sectionRepository;
        this.eventRepository = eventRepository;
        this.lockingStrategy = lockingStrategy;
        this.auditService = auditService;
        this.metricsService = metricsService;
        this.webSocketHandler = webSocketHandler;
    }

    /** Get the full seat map for an event, grouped by section. */
    @Transactional(readOnly = true)
    public SeatMapResponse getSeatMap(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new RuntimeException("Event not found: " + eventId));

        List<Seat> seats = seatRepository.findByEventId(eventId);

        Map<String, List<SeatDto>> sectionSeats = seats.stream()
                .collect(Collectors.groupingBy(
                        Seat::getSectionName,
                        TreeMap::new,
                        Collectors.mapping(this::toSeatDto, Collectors.toList())
                ));

        sectionSeats.values().forEach(list ->
                list.sort(Comparator.comparingInt(SeatDto::rowNumber)
                        .thenComparingInt(SeatDto::seatNumber)));

        int available = 0, locked = 0, booked = 0;
        for (Seat s : seats) {
            switch (s.getStatus()) {
                case AVAILABLE -> available++;
                case LOCKED -> locked++;
                case BOOKED -> booked++;
            }
        }

        return new SeatMapResponse(
                eventId, event.getName(), seats.size(),
                available, locked, booked, sectionSeats);
    }

    /** Lock a specific seat for a user. */
    @Transactional
    public SeatDto lockSeat(Long eventId, Long seatId, UUID userId) {
        Optional<Seat> locked = lockingStrategy.tryLockSeat(seatId, userId);

        if (locked.isEmpty()) {
            metricsService.recordLockContention();
            throw new SeatAlreadyLockedException(seatId);
        }

        Seat seat = locked.get();
        auditService.logSeatEvent(seatId, eventId,
                SeatStatus.AVAILABLE.name(), SeatStatus.LOCKED.name(),
                userId, ActorType.USER, "Seat selected by user");

        // Notify all pods via Postgres LISTEN/NOTIFY
        webSocketHandler.notifySeatUpdate(seat);

        return toSeatDto(seat);
    }

    /** Release a seat lock (user cancellation). */
    @Transactional
    public void releaseSeatLock(Long eventId, Long seatId, UUID userId) {
        lockingStrategy.releaseLock(seatId, userId);

        auditService.logSeatEvent(seatId, eventId,
                SeatStatus.LOCKED.name(), SeatStatus.AVAILABLE.name(),
                userId, ActorType.USER, "User cancelled seat selection");

        // Re-fetch to get updated status for notification
        seatRepository.findById(seatId).ifPresent(webSocketHandler::notifySeatUpdate);
    }

    private SeatDto toSeatDto(Seat seat) {
        return new SeatDto(
                seat.getId(),
                seat.getSectionName(),
                seat.getRowNumber(),
                seat.getSeatNumber(),
                seat.getStatus().name(),
                seat.getLockedBy(),
                seat.getLabel());
    }
}
