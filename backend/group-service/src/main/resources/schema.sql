-- Additive setup, run explicitly using the backend database owner. No cross-service foreign keys.
BEGIN;
CREATE TABLE IF NOT EXISTS study_groups (
 group_id uuid PRIMARY KEY, group_name varchar(100) NOT NULL CHECK (length(trim(group_name)) > 0),
 description varchar(2000) NOT NULL DEFAULT '', course_code varchar(32) NOT NULL, leader_id uuid NOT NULL,
 max_group_size integer NOT NULL CHECK (max_group_size >= 2),
 status varchar(16) NOT NULL CHECK (status IN ('ACTIVE','CLOSED')),
 meeting_mode varchar(16) NOT NULL CHECK (meeting_mode IN ('ONLINE','IN_PERSON','EITHER')),
 study_goals text[] NOT NULL DEFAULT '{}' CHECK (study_goals <@ ARRAY['CONCEPT_REVIEW','PROBLEM_SOLVING','EXAM_PREPARATION','PROJECT_DISCUSSION']::text[]),
 availability jsonb NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(availability) = 'array'),
 created_at timestamptz NOT NULL DEFAULT now(), updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE IF NOT EXISTS study_group_members (
 group_id uuid NOT NULL REFERENCES study_groups(group_id) ON DELETE CASCADE,
 student_id uuid NOT NULL, joined_at timestamptz NOT NULL DEFAULT now(), PRIMARY KEY(group_id,student_id)
);
CREATE INDEX IF NOT EXISTS study_group_members_student ON study_group_members(student_id);
CREATE TABLE IF NOT EXISTS group_join_requests (
 group_join_request_id uuid PRIMARY KEY, group_id uuid NOT NULL REFERENCES study_groups(group_id) ON DELETE CASCADE,
 student_id uuid NOT NULL, status varchar(16) NOT NULL,
 CONSTRAINT group_join_requests_status_check CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),
 created_at timestamptz NOT NULL, responded_at timestamptz,
 CHECK ((status='PENDING' AND responded_at IS NULL) OR (status<>'PENDING' AND responded_at IS NOT NULL))
);
CREATE UNIQUE INDEX IF NOT EXISTS one_pending_group_request ON group_join_requests(group_id,student_id) WHERE status='PENDING';
CREATE INDEX IF NOT EXISTS group_requests_by_group ON group_join_requests(group_id,status);
CREATE INDEX IF NOT EXISTS group_requests_by_student ON group_join_requests(student_id,created_at DESC);
ALTER TABLE study_groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE study_group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_join_requests ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON study_groups,study_group_members,group_join_requests FROM PUBLIC;
-- Supabase roles are absent on plain local PostgreSQL. Never change existing tables' grants.
DO $block$
BEGIN
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN
  REVOKE ALL ON study_groups,study_group_members,group_join_requests FROM anon;
 END IF;
 IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN
  REVOKE ALL ON study_groups,study_group_members,group_join_requests FROM authenticated;
 END IF;
END $block$;
COMMIT;
