package com.studybuddy.matching.controller;

import com.studybuddy.matching.engine.MatchingEngine;
import com.studybuddy.matching.model.MatchResult;
import com.studybuddy.matching.model.SearchCriteria;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/matching")
public class MatchingController {

	private final MatchingEngine matchingEngine;

	public MatchingController(MatchingEngine matchingEngine) {
		this.matchingEngine = matchingEngine;
	}

	/**
	 * Ranked study buddy suggestions for one student.
	 */
	@PostMapping("/students/{studentId}/search")
	public List<MatchResult> findMatches(@PathVariable String studentId,
										 @RequestBody SearchCriteria criteria) {
		return matchingEngine.findMatches(studentId, criteria);
	}
}
