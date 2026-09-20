package com.studybuddy.matching.model;

/**
 * The dimensions a match can be scored on.
 *
 * Not in the common module: only matching-service ever reads this.
 */
public enum CriterionType {
	COURSE,
	AVAILABILITY,
	STUDY_MODE,
	STUDY_GOAL,
	GROUP_SIZE
}
