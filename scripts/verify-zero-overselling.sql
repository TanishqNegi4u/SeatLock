-- 1. Check total booked seats
SELECT count(*) AS total_booked_seats FROM seats WHERE status = 'BOOKED';

-- 2. Check total confirmed bookings
SELECT count(*) AS total_confirmed_bookings FROM bookings WHERE status = 'CONFIRMED';

-- 3. Oversell check — MUST RETURN 0 ROWS
SELECT seat_id, count(*) AS booking_count 
FROM bookings 
WHERE status = 'CONFIRMED' 
GROUP BY seat_id 
HAVING count(*) > 1;

-- 4. Audit trail summary by status transition and actor type
SELECT to_status, actor_type, count(*) AS event_count
FROM seat_event_log
GROUP BY to_status, actor_type
ORDER BY event_count DESC;
