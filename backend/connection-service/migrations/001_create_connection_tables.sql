-- Apply once to the Supabase PostgreSQL database using the database owner.
BEGIN;

CREATE TABLE IF NOT EXISTS match_requests (
  request_id uuid PRIMARY KEY,
  sender_id uuid NOT NULL,
  recipient_id uuid NOT NULL,
  message varchar(500) NOT NULL DEFAULT '',
  status varchar(16) NOT NULL
    CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
  created_at timestamptz NOT NULL,
  responded_at timestamptz,
  CHECK (sender_id <> recipient_id),
  CHECK ((status = 'PENDING' AND responded_at IS NULL)
      OR (status <> 'PENDING' AND responded_at IS NOT NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS one_pending_match_request_per_direction
  ON match_requests(sender_id, recipient_id)
  WHERE status = 'PENDING';
CREATE INDEX IF NOT EXISTS match_requests_by_recipient
  ON match_requests(recipient_id, created_at DESC);
CREATE INDEX IF NOT EXISTS match_requests_by_sender
  ON match_requests(sender_id, created_at DESC);

CREATE TABLE IF NOT EXISTS study_buddy_connections (
  connection_id uuid PRIMARY KEY,
  student_a_id uuid NOT NULL,
  student_b_id uuid NOT NULL,
  started_at timestamptz NOT NULL,
  ended_at timestamptz,
  active boolean NOT NULL DEFAULT true,
  CHECK (student_a_id <> student_b_id),
  CHECK (active = (ended_at IS NULL))
);

CREATE UNIQUE INDEX IF NOT EXISTS one_active_connection_per_pair
  ON study_buddy_connections(
    LEAST(student_a_id, student_b_id),
    GREATEST(student_a_id, student_b_id)
  )
  WHERE active;
CREATE INDEX IF NOT EXISTS active_connections_by_student_a
  ON study_buddy_connections(student_a_id)
  WHERE active;
CREATE INDEX IF NOT EXISTS active_connections_by_student_b
  ON study_buddy_connections(student_b_id)
  WHERE active;

ALTER TABLE match_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_buddy_connections ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON match_requests, study_buddy_connections FROM PUBLIC;
DO $block$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE ALL ON match_requests, study_buddy_connections FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE ALL ON match_requests, study_buddy_connections FROM authenticated;
  END IF;
END $block$;

COMMIT;
