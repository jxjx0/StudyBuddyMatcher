package com.studybuddy.matching.engine;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.matching.model.CriterionType;
import com.studybuddy.matching.model.MatchingCriterion;

/**
 * Gives timetable overlap the largest share of the weighting.
 *
 * The administrator chooses the weights; this strategy only enforces its rule
 * that AVAILABILITY must carry the highest weight. Weights that follow the rule
 * are returned unchanged.
 */
@Component
public class AvailabilityFirstMatchingStrategy implements MatchingStrategy {

	@Override
	public String getName() {
		return "AVAILABILITY_FIRST";
	}

	@Override
	public List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria) {
		if (criteria == null || criteria.isEmpty()) {
			return new ArrayList<>();
		}

		// Find the weight given to AVAILABILITY. It must be enabled for this strategy to make sense.
		MatchingCriterion availability = null;
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null && criterion.isEnabled() && criterion.getType() == CriterionType.AVAILABILITY) {
				availability = criterion;
			}
		}
		if (availability == null) {
			throw new IllegalArgumentException("Availability-first matching requires the availability criterion to be enabled");
		}

		// Every other enabled criterion must weigh strictly less than AVAILABILITY.
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null && criterion != availability && criterion.isEnabled()
					&& criterion.getWeight() >= availability.getWeight()) {
				throw new IllegalArgumentException("Availability-first matching requires availability to have the highest weight");
			}
		}

		// Return copies so the stored configuration can never be changed through this list.
		List<MatchingCriterion> weighted = new ArrayList<>();
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null) {
				weighted.add(new MatchingCriterion(criterion.getType(), criterion.getWeight(), criterion.isEnabled()));
			}
		}
		return weighted;
	}
}
