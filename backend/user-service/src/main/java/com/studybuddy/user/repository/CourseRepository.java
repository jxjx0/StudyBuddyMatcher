package com.studybuddy.user.repository;

import com.studybuddy.user.model.Course;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class CourseRepository {
	private final JdbcTemplate jdbc;
	public CourseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
	public List<Course> findAll() {
		return jdbc.query("SELECT course_code, course_name FROM courses ORDER BY course_code",
				(rs, row) -> new Course(rs.getString("course_code"), rs.getString("course_name")));
	}
	public Optional<Course> findByCode(String courseCode) {
		return jdbc.query("SELECT course_code, course_name FROM courses WHERE course_code = ?",
				(rs, row) -> new Course(rs.getString("course_code"), rs.getString("course_name")), courseCode)
				.stream().findFirst();
	}
	public Course save(Course course) {
		jdbc.update("INSERT INTO courses(course_code, course_name) VALUES (?, ?) ON CONFLICT (course_code) DO UPDATE SET course_name = EXCLUDED.course_name",
				course.getCourseCode().trim().toUpperCase(), course.getCourseName().trim());
		return course;
	}
}
