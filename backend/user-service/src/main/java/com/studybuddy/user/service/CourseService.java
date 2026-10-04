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
		return courseRepository.findAll().stream().map(course -> new CourseDto(course.getCourseCode(), course.getCourseName())).toList();
	}

	public CourseDto findByCode(String courseCode) {
		var course = courseRepository.findByCode(courseCode).orElseThrow(() -> new AccountNotFoundException("Course not found"));
		return new CourseDto(course.getCourseCode(), course.getCourseName());
	}

	public CourseDto create(CourseDto course) {
		var saved = courseRepository.save(new com.studybuddy.user.model.Course(course.getCourseCode(), course.getCourseName()));
		return new CourseDto(saved.getCourseCode(), saved.getCourseName());
	}
}
