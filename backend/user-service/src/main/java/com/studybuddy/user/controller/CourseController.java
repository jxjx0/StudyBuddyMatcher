package com.studybuddy.user.controller;

import com.studybuddy.user.dto.CourseDto;
import com.studybuddy.user.service.CourseService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/courses")
public class CourseController {

	private final CourseService courseService;

	public CourseController(CourseService courseService) {
		this.courseService = courseService;
	}

	@GetMapping
	public List<CourseDto> findAll() {
		return courseService.findAll();
	}

	@GetMapping("/{courseCode}")
	public CourseDto findByCode(@PathVariable String courseCode) {
		return courseService.findByCode(courseCode);
	}

	@PostMapping
	public ResponseEntity<CourseDto> create(@RequestBody CourseDto course) {
		return ResponseEntity.status(HttpStatus.CREATED).body(courseService.create(course));
	}
}
