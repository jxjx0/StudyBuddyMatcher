CREATE TABLE IF NOT EXISTS user_accounts (
    user_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    user_role VARCHAR(16) NOT NULL CHECK (user_role IN ('STUDENT', 'ADMIN')),
    account_status VARCHAR(16) NOT NULL CHECK (account_status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    profile_data JSONB NOT NULL DEFAULT '{}'::jsonb
);

CREATE TABLE IF NOT EXISTS courses (
    course_id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_code VARCHAR(32) NOT NULL,
    course_name VARCHAR(200) NOT NULL
);

CREATE TABLE IF NOT EXISTS students (
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,
    school VARCHAR(200) NOT NULL,
    programme VARCHAR(200) NOT NULL,
    year_of_study INTEGER NOT NULL,
    contact_number VARCHAR(32),
    current_courses UUID[] DEFAULT '{}'::uuid[],
    courses_for_studying UUID[] DEFAULT '{}'::uuid[],
    meeting_mode VARCHAR(16),
    weekly_availability JSONB DEFAULT '[]'::jsonb,
    group_size_preference VARCHAR(16),
    study_goals TEXT[] DEFAULT '{}'::text[],
    CONSTRAINT students_pkey PRIMARY KEY (user_id),
    CONSTRAINT students_user_id_fkey FOREIGN KEY (user_id) REFERENCES user_accounts (user_id) ON DELETE CASCADE,
    CONSTRAINT students_group_size_preference_check CHECK (group_size_preference IN ('ONE_TO_ONE', 'SMALL_GROUP', 'EITHER')),
    CONSTRAINT students_meeting_mode_check CHECK (meeting_mode IN ('IN_PERSON', 'ONLINE', 'EITHER')),
    CONSTRAINT students_study_goals_check CHECK (study_goals <@ ARRAY['CONCEPT_REVIEW', 'PROBLEM_SOLVING', 'EXAM_PREPARATION', 'PROJECT_DISCUSSION']::text[]),
    CONSTRAINT students_year_of_study_check CHECK (year_of_study BETWEEN 1 AND 5)
);
