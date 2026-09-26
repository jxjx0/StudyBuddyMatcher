package com.studybuddy.user.controller;

import com.studybuddy.user.dto.StudentContactDto;
import com.studybuddy.user.dto.StudentDto;
import com.studybuddy.user.dto.StudentRequest;
import com.studybuddy.user.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Student profile endpoints.
 *
 * This controller is the shape the other services follow: it maps HTTP to
 * method calls and validates input, and does nothing else. All logic sits in
 * the service layer.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

	private final StudentService studentService;

	public StudentController(StudentService studentService) {
		this.studentService = studentService;
	}

	@GetMapping
	public List<StudentDto> findAll() {
		return studentService.findAll();
	}

	@GetMapping("/{userId}")
	public StudentDto findById(@PathVariable String userId) {
		return studentService.findById(userId);
	}

	/**
	 * Contact details for a matched student. Called by connection-service once
	 * a match request has been accepted, not by the browser.
	 *
	 * TODO: restrict this once authentication exists - right now anyone who can
	 * reach the service can call it.
	 */
	@GetMapping("/{userId}/contact")
	public StudentContactDto findContactDetails(@PathVariable String userId) {
		return studentService.findContactDetails(userId);
	}

	@PostMapping
	public ResponseEntity<StudentDto> create(@Valid @RequestBody StudentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(request));
	}

	@PutMapping("/{userId}")
	public StudentDto update(@PathVariable String userId,
							 @Valid @RequestBody StudentRequest request) {
		return studentService.update(userId, request);
	}
}
