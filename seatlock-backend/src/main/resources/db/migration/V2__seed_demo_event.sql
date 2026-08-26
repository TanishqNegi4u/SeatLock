-- V2: Seed demo event with 5 sections x 10 rows x 10 seats = 500 seats
-- Sections: A, B, C, D, E (theater-style layout)

INSERT INTO events (name, event_date, status)
VALUES ('SeatLock Demo Concert', '2025-12-31 20:00:00+00', 'ACTIVE');

-- Create 5 sections for event_id = 1
INSERT INTO sections (event_id, name, row_count, seats_per_row) VALUES
    (1, 'A', 10, 10),
    (1, 'B', 10, 10),
    (1, 'C', 10, 10),
    (1, 'D', 10, 10),
    (1, 'E', 10, 10);

-- Generate 500 seats using a cross join of sections x rows x seats
-- This uses generate_series to create all seat combinations
INSERT INTO seats (section_id, event_id, section_name, row_number, seat_number, status)
SELECT
    s.id,
    s.event_id,
    s.name,
    r.row_num,
    c.seat_num,
    'AVAILABLE'
FROM sections s
CROSS JOIN generate_series(1, 10) AS r(row_num)
CROSS JOIN generate_series(1, 10) AS c(seat_num)
WHERE s.event_id = 1
ORDER BY s.name, r.row_num, c.seat_num;
