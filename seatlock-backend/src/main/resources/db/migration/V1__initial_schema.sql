-- V1: SeatLock initial schema
-- All tables for the ticket-booking system with constraints and indexes.

CREATE TABLE events (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    event_date  TIMESTAMP WITH TIME ZONE NOT NULL,
    status      VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('ACTIVE', 'CANCELLED', 'COMPLETED')),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE sections (
    id             BIGSERIAL PRIMARY KEY,
    event_id       BIGINT NOT NULL REFERENCES events(id),
    name           VARCHAR(10) NOT NULL,
    row_count      INT NOT NULL DEFAULT 10,
    seats_per_row  INT NOT NULL DEFAULT 10,
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (event_id, name)
);
CREATE INDEX idx_sections_event_id ON sections(event_id);

CREATE TABLE seats (
    id            BIGSERIAL PRIMARY KEY,
    section_id    BIGINT NOT NULL REFERENCES sections(id),
    event_id      BIGINT NOT NULL REFERENCES events(id),
    section_name  VARCHAR(10) NOT NULL,
    row_number    INT NOT NULL,
    seat_number   INT NOT NULL,
    status        VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE'
                  CHECK (status IN ('AVAILABLE', 'LOCKED', 'BOOKED')),
    locked_by     UUID,
    locked_at     TIMESTAMP WITH TIME ZONE,
    version       INT NOT NULL DEFAULT 0,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    updated_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    UNIQUE (event_id, section_name, row_number, seat_number)
);
CREATE INDEX idx_seats_event_status ON seats(event_id, status);
CREATE INDEX idx_seats_locked_at ON seats(status, locked_at) WHERE status = 'LOCKED';
CREATE INDEX idx_seats_section ON seats(section_id);

CREATE TABLE bookings (
    id              BIGSERIAL PRIMARY KEY,
    seat_id         BIGINT NOT NULL REFERENCES seats(id),
    event_id        BIGINT NOT NULL REFERENCES events(id),
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'CONFIRMED'
                    CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    idempotency_key UUID NOT NULL UNIQUE,
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    confirmed_at    TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_bookings_seat ON bookings(seat_id);
CREATE INDEX idx_bookings_user ON bookings(user_id, event_id);

CREATE TABLE booking_requests (
    id              BIGSERIAL PRIMARY KEY,
    idempotency_key UUID NOT NULL UNIQUE,
    seat_id         BIGINT NOT NULL,
    event_id        BIGINT NOT NULL,
    user_id         UUID NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE waiting_room_entries (
    id          BIGSERIAL PRIMARY KEY,
    event_id    BIGINT NOT NULL REFERENCES events(id),
    user_id     UUID NOT NULL,
    joined_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    status      VARCHAR(20) NOT NULL DEFAULT 'WAITING'
                CHECK (status IN ('WAITING', 'ADMITTED', 'EXPIRED', 'LEFT')),
    position    INT,
    admitted_at TIMESTAMP WITH TIME ZONE,
    UNIQUE (event_id, user_id)
);
CREATE INDEX idx_wr_event_status ON waiting_room_entries(event_id, status);
CREATE INDEX idx_wr_position ON waiting_room_entries(event_id, position) WHERE status = 'WAITING';

CREATE TABLE seat_event_log (
    id             BIGSERIAL PRIMARY KEY,
    seat_id        BIGINT NOT NULL REFERENCES seats(id),
    event_id       BIGINT NOT NULL,
    from_status    VARCHAR(20),
    to_status      VARCHAR(20) NOT NULL,
    actor_user_id  UUID,
    actor_type     VARCHAR(20) NOT NULL
                   CHECK (actor_type IN ('USER', 'REAPER', 'SYSTEM', 'ADMIN')),
    reason         VARCHAR(255),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);
CREATE INDEX idx_sel_seat ON seat_event_log(seat_id);
CREATE INDEX idx_sel_event ON seat_event_log(event_id);
