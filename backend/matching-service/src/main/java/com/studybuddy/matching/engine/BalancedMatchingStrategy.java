package com.studybuddy.matching.engine;

import com.studybuddy.matching.model.MatchingCriterion;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Weights every enabled criterion evenly.
 */
@Component
public class BalancedMatchingStrategy implements MatchingStrategy {

	@Override
	public String getName() {
		return "BALANCED";
	}

	@Override
	public List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria) {
		// TODO: distribute weight evenly across all enabled criteria
		return criteria;
	}
}
