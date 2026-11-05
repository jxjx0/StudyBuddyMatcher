-- Local-only fixture: seeds courses for the disposable demo database.
-- The students table is created by backend/user-service/src/main/resources/schema.sql.
-- Never apply this fixture to the team's shared database.
INSERT INTO courses(course_code,course_name) VALUES ('IS442','Object Oriented Programming'),('IS216','Web Application Development 2');
