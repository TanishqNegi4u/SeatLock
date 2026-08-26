package com.seatlock.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "waiting_room_entries", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "user_id"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class WaitingRoomEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "joined_at", nullable = false, updatable = false)
    private Instant joinedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private WaitingRoomStatus status;

    @Column
    private Integer position;

    @Column(name = "admitted_at")
    private Instant admittedAt;

    @PrePersist
    protected void onCreate() {
        if (joinedAt == null) joinedAt = Instant.now();
        if (status == null) status = WaitingRoomStatus.WAITING;
    }
}
