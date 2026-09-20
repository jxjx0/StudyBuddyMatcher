package com.studybuddy.matching.engine;

import com.studybuddy.matching.model.MatchingCriterion;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives course alignment the largest share of the weighting.
 */
@Component
public class CourseFirstMatchingStrategy implements MatchingStrategy {

	@Override
	public String getName() {
		return "COURSE_FIRST";
	}

	@Override
	public List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria) {
		// TODO: ensure COURSE carries the highest weight, then spread the rest
		return criteria;
	}
}
