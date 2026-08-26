-- V3: Add pod_hostname to seat_event_log for multi-replica observability
ALTER TABLE seat_event_log ADD COLUMN IF NOT EXISTS pod_hostname VARCHAR(100);
