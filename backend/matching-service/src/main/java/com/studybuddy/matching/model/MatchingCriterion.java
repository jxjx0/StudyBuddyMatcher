package com.studybuddy.matching.model;

/**
 * One scoring dimension and how much it counts towards the overall score.
 */
public class MatchingCriterion {

	private CriterionType type;
	private double weight;
	private boolean enabled = true;

	public MatchingCriterion() {
	}

	public MatchingCriterion(CriterionType type, double weight, boolean enabled) {
		this.type = type;
		this.weight = weight;
		this.enabled = enabled;
	}

	public CriterionType getType() {
		return type;
	}

	public void setType(CriterionType type) {
		this.type = type;
	}

	public double getWeight() {
		return weight;
	}

	public void setWeight(double weight) {
		this.weight = weight;
	}

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}
}
