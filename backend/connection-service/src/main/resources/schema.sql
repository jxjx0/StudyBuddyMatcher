CREATE TABLE IF NOT EXISTS match_requests (
    request_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    sender_id UUID NOT NULL,
    recipient_id UUID NOT NULL,
    message VARCHAR(500),
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'CANCELLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    responded_at TIMESTAMPTZ
);

CREATE UNIQUE INDEX IF NOT EXISTS one_pending_match_request
ON match_requests (sender_id, recipient_id)
WHERE status = 'PENDING';

CREATE INDEX IF NOT EXISTS match_requests_recipient_status
ON match_requests (recipient_id, status);

CREATE INDEX IF NOT EXISTS match_requests_sender_status
ON match_requests (sender_id, status);

CREATE TABLE IF NOT EXISTS study_buddy_connections (
    connection_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    student_a_id UUID NOT NULL,
    student_b_id UUID NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    ended_at TIMESTAMPTZ,
    active BOOLEAN NOT NULL DEFAULT true
);

CREATE INDEX IF NOT EXISTS study_buddy_connections_active_a
ON study_buddy_connections (student_a_id)
WHERE active = true;

CREATE INDEX IF NOT EXISTS study_buddy_connections_active_b
ON study_buddy_connections (student_b_id)
WHERE active = true;

CREATE TABLE IF NOT EXISTS connection_chat_messages (
    message_id UUID PRIMARY KEY,
    connection_id UUID NOT NULL
        REFERENCES study_buddy_connections (connection_id) ON DELETE CASCADE,
    sender_id UUID NOT NULL,
    content VARCHAR(2000) NOT NULL CHECK (length(trim(content)) > 0),
    sent_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS connection_chat_messages_by_connection
ON connection_chat_messages (connection_id, sent_at DESC, message_id);

CREATE TABLE IF NOT EXISTS connection_chat_deletions (
    connection_id UUID NOT NULL
        REFERENCES study_buddy_connections (connection_id) ON DELETE CASCADE,
    student_id UUID NOT NULL,
    deleted_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (connection_id, student_id)
);
