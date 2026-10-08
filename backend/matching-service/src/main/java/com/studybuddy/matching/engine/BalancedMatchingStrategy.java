package com.studybuddy.matching.engine;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.studybuddy.matching.model.MatchingCriterion;

/**
 * Weights every enabled criterion evenly.
 *
 * Unlike the "first" strategies, this one replaces the administrator's weights:
 * a balanced scheme means every enabled criterion gets the same share of 100.
 */
@Component
public class BalancedMatchingStrategy implements MatchingStrategy {

	@Override
	public String getName() {
		return "BALANCED";
	}

	@Override
	public List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria) {
		if (criteria == null || criteria.isEmpty()) {
			return new ArrayList<>();
		}

		int enabledCount = 0;
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null && criterion.isEnabled()) {
				enabledCount++;
			}
		}
		if (enabledCount == 0) {
			throw new IllegalArgumentException("Balanced matching requires at least one enabled criterion");
		}

		// Build new criteria rather than calling setWeight, so the stored configuration is never changed.
		double equalShare = 100.0 / enabledCount;
		List<MatchingCriterion> balanced = new ArrayList<>();
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null) {
				double weight = criterion.isEnabled() ? equalShare : 0;
				balanced.add(new MatchingCriterion(criterion.getType(), weight, criterion.isEnabled()));
			}
		}
		return balanced;
	}
}
