-- Local-only fixture: user-service currently omits students DDL from its bootstrap.
-- Never apply this fixture to the team's shared database.
CREATE TABLE students (
 user_id uuid PRIMARY KEY REFERENCES user_accounts(user_id) ON DELETE CASCADE,
 name varchar(100) NOT NULL, school varchar(200) NOT NULL, programme varchar(200) NOT NULL,
 year_of_study integer NOT NULL CHECK(year_of_study BETWEEN 1 AND 5), contact_number varchar(32),
 current_courses uuid[] NOT NULL DEFAULT '{}', courses_for_studying uuid[] NOT NULL DEFAULT '{}',
 meeting_mode varchar(16), weekly_availability jsonb NOT NULL DEFAULT '[]',
 group_size_preference varchar(16), study_goals text[] NOT NULL DEFAULT '{}'
);
INSERT INTO courses(course_code,course_name) VALUES ('IS442','Object Oriented Programming'),('IS216','Web Application Development 2');
