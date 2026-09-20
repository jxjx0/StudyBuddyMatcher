package com.studybuddy.matching.model;

import com.studybuddy.common.dto.StudentDto;

import java.util.ArrayList;
import java.util.List;

/**
 * One candidate in a ranked list of matches, with the score that put it there.
 *
 * The candidate is a StudentDto rather than a Student - matching-service does
 * not own the Student class, it receives student data from user-service.
 */
public class MatchResult {

	private StudentDto candidate;
	private double score;
	private List<CriterionType> matchedCriteria = new ArrayList<>();

	public MatchResult() {
	}

	public MatchResult(StudentDto candidate, double score, List<CriterionType> matchedCriteria) {
		this.candidate = candidate;
		this.score = score;
		this.matchedCriteria = matchedCriteria;
	}

	public StudentDto getCandidate() {
		return candidate;
	}

	public void setCandidate(StudentDto candidate) {
		this.candidate = candidate;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public List<CriterionType> getMatchedCriteria() {
		return matchedCriteria;
	}

	public void setMatchedCriteria(List<CriterionType> matchedCriteria) {
		this.matchedCriteria = matchedCriteria;
	}
}
