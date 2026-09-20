package com.studybuddy.matching.model;

import com.studybuddy.matching.engine.MatchingStrategy;

import java.util.ArrayList;
import java.util.List;

/**
 * The matching setup currently in effect: which strategy is active and the
 * criteria it weights. Changed by an administrator at runtime.
 */
public class MatchingConfig {

	private MatchingStrategy strategy;
	private List<MatchingCriterion> criteria = new ArrayList<>();

	public MatchingConfig() {
	}

	public MatchingStrategy getStrategy() {
		return strategy;
	}

	public void setStrategy(MatchingStrategy strategy) {
		this.strategy = strategy;
	}

	public List<MatchingCriterion> getCriteria() {
		return criteria;
	}

	public void setCriteria(List<MatchingCriterion> criteria) {
		this.criteria = criteria;
	}
}
