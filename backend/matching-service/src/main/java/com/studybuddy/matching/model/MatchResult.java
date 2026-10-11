package com.studybuddy.matching.model;

import com.studybuddy.matching.dto.StudentDto;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MatchResult {

	private StudentDto candidate;
	private double score;
	private List<CriterionType> matchedCriteria = new ArrayList<>();

	public MatchResult() {
	}

	public MatchResult(StudentDto candidate, double score, List<String> matchedCriteria) {
		this.candidate = candidate;
		this.score = score;
		setMatchedCriteria(matchedCriteria);
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

	public List<String> getMatchedCriteria() {
		List<String> names = new ArrayList<>();
		for (CriterionType type : matchedCriteria) {
			names.add(type.name());
		}
		return names;
	}

	public void setMatchedCriteria(List<String> matchedCriteria) {
		this.matchedCriteria = new ArrayList<>();
		if (matchedCriteria != null) {
			for (String name : matchedCriteria) {
				if (name != null && !name.isBlank()) {
					this.matchedCriteria.add(CriterionType.valueOf(name.trim().toUpperCase(Locale.ROOT)));
				}
			}
		}
	}
}