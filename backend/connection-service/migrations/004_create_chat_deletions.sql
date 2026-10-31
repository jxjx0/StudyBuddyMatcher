-- Apply once to the Supabase PostgreSQL database using the database owner.
BEGIN;

CREATE TABLE IF NOT EXISTS connection_chat_deletions (
  connection_id uuid NOT NULL
    REFERENCES study_buddy_connections(connection_id) ON DELETE CASCADE,
  student_id uuid NOT NULL,
  deleted_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (connection_id, student_id)
);

ALTER TABLE connection_chat_deletions ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON connection_chat_deletions FROM PUBLIC;
DO $block$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE ALL ON connection_chat_deletions FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE ALL ON connection_chat_deletions FROM authenticated;
  END IF;
END $block$;

COMMIT;
