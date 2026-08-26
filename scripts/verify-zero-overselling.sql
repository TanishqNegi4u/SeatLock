-- Check total booked seats
SELECT count(*) AS total_booked_seats FROM seats WHERE status = 'BOOKED';

-- Check total confirmed bookings
SELECT count(*) AS total_confirmed_bookings FROM bookings WHERE status = 'CONFIRMED';

-- Oversell check - MUST RETURN 0 ROWS
SELECT seat_id, count(*) AS booking_count 
FROM bookings 
GROUP BY seat_id 
HAVING count(*) > 1;

-- Contention and audit log summary
SELECT action, count(*) AS action_count
FROM audit_logs
GROUP BY action
ORDER BY action_count DESC;
