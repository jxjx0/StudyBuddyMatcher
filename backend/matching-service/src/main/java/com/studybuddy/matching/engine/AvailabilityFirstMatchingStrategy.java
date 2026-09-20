package com.studybuddy.matching.engine;

import com.studybuddy.matching.model.MatchingCriterion;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Gives timetable overlap the largest share of the weighting.
 */
@Component
public class AvailabilityFirstMatchingStrategy implements MatchingStrategy {

	@Override
	public String getName() {
		return "AVAILABILITY_FIRST";
	}

	@Override
	public List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria) {
		// TODO: ensure AVAILABILITY carries the highest weight, then spread the rest
		return criteria;
	}
}
