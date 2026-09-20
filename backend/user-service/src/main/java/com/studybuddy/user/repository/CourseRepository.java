package com.studybuddy.user.repository;

import com.studybuddy.user.model.Course;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Storage for courses. Stubbed - see {@link UserRepository} for the reasoning.
 */
@Repository
public class CourseRepository {

	public List<Course> findAll() {
		// TODO: implement
		return List.of();
	}

	public Optional<Course> findByCode(String courseCode) {
		// TODO: implement
		return Optional.empty();
	}

	public Course save(Course course) {
		// TODO: implement
		return course;
	}
}
