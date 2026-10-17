-- Apply once to existing group-service databases using the database owner.
BEGIN;
ALTER TABLE group_join_requests
  DROP CONSTRAINT IF EXISTS group_join_requests_status_check;
ALTER TABLE group_join_requests
  ADD CONSTRAINT group_join_requests_status_check
  CHECK (status IN ('PENDING', 'ACCEPTED', 'REJECTED', 'CANCELLED'));
COMMIT;
