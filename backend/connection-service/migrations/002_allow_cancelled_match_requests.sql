-- Apply once if match_requests was created before CANCELLED was supported.
BEGIN;

ALTER TABLE match_requests
  DROP CONSTRAINT IF EXISTS match_requests_status_check;
ALTER TABLE match_requests
  ADD CONSTRAINT match_requests_status_check
  CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED'));

COMMIT;
