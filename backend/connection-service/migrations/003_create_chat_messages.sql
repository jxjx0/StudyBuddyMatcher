-- Apply once to the Supabase PostgreSQL database using the database owner.
BEGIN;

CREATE TABLE IF NOT EXISTS connection_chat_messages (
  message_id uuid PRIMARY KEY,
  connection_id uuid NOT NULL
    REFERENCES study_buddy_connections(connection_id) ON DELETE CASCADE,
  sender_id uuid NOT NULL,
  content varchar(2000) NOT NULL CHECK (length(trim(content)) > 0),
  sent_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS connection_chat_messages_by_connection
  ON connection_chat_messages(connection_id, sent_at DESC, message_id);

ALTER TABLE connection_chat_messages ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON connection_chat_messages FROM PUBLIC;
DO $block$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'anon') THEN
    REVOKE ALL ON connection_chat_messages FROM anon;
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'authenticated') THEN
    REVOKE ALL ON connection_chat_messages FROM authenticated;
  END IF;
END $block$;

COMMIT;
