package com.studybuddy.matching.model;

import com.studybuddy.matching.engine.MatchingStrategy;

import java.util.ArrayList;
import java.util.List;

public class MatchingConfig {

	private MatchingStrategy strategy;
	private List<MatchingCriterion> criteria = List.of();

	public MatchingConfig() {
	}

	public MatchingStrategy getStrategy() {
		return strategy;
	}

	public void setStrategy(MatchingStrategy strategy) {
		this.strategy = strategy;
	}

	/** Returns a defensive copy so callers cannot mutate the active weights. */
	public List<MatchingCriterion> getCriteria() {
		List<MatchingCriterion> copy = new ArrayList<>(criteria.size());
		for (MatchingCriterion criterion : criteria) {
			if (criterion != null) {
				copy.add(new MatchingCriterion(criterion.getType(), criterion.getWeight(), criterion.isEnabled()));
			}
		}
		return copy;
	}

	/** Stores a defensive copy; a null list means no criteria are configured. */
	public void setCriteria(List<MatchingCriterion> criteria) {
		List<MatchingCriterion> copy = new ArrayList<>();
		if (criteria != null) {
			for (MatchingCriterion criterion : criteria) {
				if (criterion != null) {
					copy.add(new MatchingCriterion(criterion.getType(), criterion.getWeight(), criterion.isEnabled()));
				}
			}
		}
		this.criteria = List.copyOf(copy);
	}
}
