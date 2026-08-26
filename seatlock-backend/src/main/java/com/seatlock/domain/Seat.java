package com.seatlock.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "seats", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"event_id", "section_name", "row_number", "seat_number"})
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Seat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "section_id", nullable = false)
    private Section section;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "section_name", nullable = false, length = 10)
    private String sectionName;

    @Column(name = "row_number", nullable = false)
    private Integer rowNumber;

    @Column(name = "seat_number", nullable = false)
    private Integer seatNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SeatStatus status;

    @Column(name = "locked_by")
    private UUID lockedBy;

    @Column(name = "locked_at")
    private Instant lockedAt;

    // Used by optimistic locking strategy (@Version)
    // Pessimistic locking ignores this column, but it's always present
    // to enable strategy switching without schema changes.
    @Version
    @Column(nullable = false)
    private Integer version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = Instant.now();
        if (updatedAt == null) updatedAt = Instant.now();
        if (status == null) status = SeatStatus.AVAILABLE;
        if (version == null) version = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    /**
     * Returns a human-readable label like "A-3-7" (section A, row 3, seat 7).
     */
    public String getLabel() {
        return sectionName + "-" + rowNumber + "-" + seatNumber;
    }
}
