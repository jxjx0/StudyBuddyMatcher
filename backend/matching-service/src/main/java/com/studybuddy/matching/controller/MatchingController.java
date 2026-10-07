package com.studybuddy.matching.controller;

import java.util.List;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
	 * Ranked study buddy suggestions for one student.
	 */
	@PostMapping("/students/{studentId}/search")
	public List<MatchResult> findMatches(
			@PathVariable String studentId,
			@RequestBody SearchCriteria criteria) {

		StudentDto student = studentClient.findStudent(studentId);

		return matchingEngine.findMatches(student, criteria);
	}
}