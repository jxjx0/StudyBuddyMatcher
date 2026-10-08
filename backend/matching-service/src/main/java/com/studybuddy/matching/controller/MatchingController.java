package com.studybuddy.matching.controller;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientException;

import com.studybuddy.matching.client.StudentClient;
import com.studybuddy.matching.dto.StudentDto;
import com.studybuddy.matching.engine.MatchingEngine;
import com.studybuddy.matching.model.MatchResult;
import com.studybuddy.matching.model.SearchCriteria;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

	private final MatchingEngine matchingEngine;
	private final StudentClient studentClient;

	public MatchingController(MatchingEngine matchingEngine, StudentClient studentClient) {
		this.matchingEngine = matchingEngine;
		this.studentClient = studentClient;
	}

	/**
	 * Ranked study buddy suggestions for one student. The body is optional:
	 * without filters every active student is scored and ranked.
	 */
	@PostMapping("/students/{studentId}/search")
	public List<MatchResult> findMatches(
			@PathVariable String studentId,
			@RequestBody(required = false) SearchCriteria criteria) {

		StudentDto student = studentClient.findStudent(studentId);
		if (student == null) {
			throw new NoSuchElementException("Student not found");
		}

		return matchingEngine.findMatches(student, criteria);
	}

	// Errors come back as {"message": ...}, the shape the frontend already shows to users.

	@ExceptionHandler(NoSuchElementException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> studentNotFound(NoSuchElementException exception) {
		return Map.of("message", exception.getMessage());
	}

	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> invalidFilters(IllegalArgumentException exception) {
		return Map.of("message", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> unreadableFilters() {
		return Map.of("message", "Check the search filters");
	}

	@ExceptionHandler(RestClientException.class)
	@ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
	public Map<String, String> studentServiceUnavailable() {
		return Map.of("message", "Student profiles are unavailable. Please try again shortly.");
	}
}
