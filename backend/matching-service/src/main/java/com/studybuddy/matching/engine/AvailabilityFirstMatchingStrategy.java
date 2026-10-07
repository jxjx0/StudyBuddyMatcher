package com.studybuddy.matching.engine;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.matching.model.CriterionType;
import com.studybuddy.matching.model.MatchingCriterion;

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
		// Return an empty list if no criteria selected/enabled
		if (criteria == null || criteria.isEmpty()) {
			return new ArrayList<>();
		}

		int enabledCount = 0;
		boolean leadEnabled = false;

		// Count enabled criteria and check if AVAILABILITY is enabled
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null && criterion.isEnabled()) {
				enabledCount++;
				if (criterion.getType() == CriterionType.AVAILABILITY) {
					leadEnabled = true;
				}
			}
		}

		List<MatchingCriterion> weighted = new ArrayList<>();

		if (enabledCount == 0) {
			// No enabled criteria, so copy the original criteria
			for (MatchingCriterion criterion : criteria) {
				if (criterion != null) {weighted.add(new MatchingCriterion(criterion.getType(), criterion.getWeight(), criterion.isEnabled()));
				}
			}
			return weighted;
		}

		double unit;

		if (leadEnabled) {
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
				// Disabled criteria get 0 weight
				weighted.add(new MatchingCriterion(criterion.getType(), 0, false));
			} 
			else if (leadEnabled && criterion.getType() == CriterionType.AVAILABILITY) {
				// AVAILABILITY gets double weight
				weighted.add(new MatchingCriterion(criterion.getType(), unit * 2, true));
			} 
			else {
				// Other enabled criteria get normal weight
				weighted.add(new MatchingCriterion(criterion.getType(), unit, true));
			}
		}
		return weighted;
	}
}
