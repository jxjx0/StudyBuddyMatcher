package com.studybuddy.matching.engine;

import com.studybuddy.matching.model.MatchingCriterion;

import java.util.List;

/**
 * Decides how the configured criteria are weighted before scoring runs.
 * Swapping the implementation changes how matches are prioritised without
 * touching the engine.
 */
public interface MatchingStrategy {

	/**
	 * Name used to select this strategy through the admin API, for example
	 * "BALANCED". Not in the UML - added so a strategy can be chosen by name
	 * rather than by class.
	 */
	String getName();

	List<MatchingCriterion> applyStrategy(List<MatchingCriterion> criteria);
}
