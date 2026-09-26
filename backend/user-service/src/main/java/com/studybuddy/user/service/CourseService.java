package com.studybuddy.user.service;

import com.studybuddy.user.dto.CourseDto;
import com.studybuddy.user.repository.CourseRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CourseService {

	private final CourseRepository courseRepository;

	public CourseService(CourseRepository courseRepository) {
		this.courseRepository = courseRepository;
	}

	public List<CourseDto> findAll() {
		// TODO: implement
		return List.of();
	}

	public CourseDto findByCode(String courseCode) {
		// TODO: implement - decide how a missing record is reported (the team has
		// not yet agreed an exception + @RestControllerAdvice approach)
		return null;
	}

	public CourseDto create(CourseDto course) {
		// TODO: implement
		return null;
	}
}
