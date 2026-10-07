package com.studybuddy.matching.engine;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.matching.model.CriterionType;
import com.studybuddy.matching.model.MatchingCriterion;

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
		if (criteria == null || criteria.isEmpty()) {
			return new ArrayList<>();
		}

		int enabledCount = 0;
		boolean leadEnabled = false;

		// Count enabled criteria and check if COURSE is enabled
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null && criterion.isEnabled()) {
				enabledCount++;
				if (criterion.getType() == CriterionType.COURSE) {
					leadEnabled = true;
				}
			}
		}

		List<MatchingCriterion> weighted = new ArrayList<>();

		if (enabledCount == 0) {
			// No enabled criteria, so copy the original criteria
			for (MatchingCriterion criterion : criteria) {
				if (criterion != null) {
					weighted.add(new MatchingCriterion(criterion.getType(), criterion.getWeight(), criterion.isEnabled()));
				}
			}

			return weighted;
		}

		double unit;

		if (leadEnabled) {
			// If COURSE is enabled, add additional weight for COURSE
			// Example: COURSE, AVAILABILITY is enabled, without +1, each criteria will have 0.50 weight
			// With +1, each criteria will have 0.33, while we assign the additional weight to COURSE = 0.67 
			unit = 100.0 / (enabledCount + 1);
		} 
		else {
			unit = 100.0 / enabledCount;
		}

		for (MatchingCriterion criterion : criteria) {
			if (criterion == null) {
				continue;
			}
			if (!criterion.isEnabled()) {
				weighted.add(new MatchingCriterion(criterion.getType(), 0, false));
			} 
			else if (leadEnabled && criterion.getType() == CriterionType.COURSE) {
				// Add more weight to COURSE criteria
				weighted.add(new MatchingCriterion(criterion.getType(), unit * 2, true));
			} 
			else {weighted.add(new MatchingCriterion(criterion.getType(), unit, true));
			}
		}
		// Return a new list with weight added to COURSE
		return weighted;
	}
}
