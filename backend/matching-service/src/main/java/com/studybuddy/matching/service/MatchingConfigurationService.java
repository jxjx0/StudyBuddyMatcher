package com.studybuddy.matching.service;

import com.studybuddy.matching.model.MatchingConfig;
import com.studybuddy.matching.model.MatchingCriterion;
import com.studybuddy.matching.engine.MatchingStrategy;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Holds the matching configuration currently in effect and applies
 * administrator changes to it.
 *
 * Spring injects every MatchingStrategy implementation into the constructor,
 * so writing a new strategy class is enough to make it selectable - nothing
 * here needs editing.
 */
@Service
public class MatchingConfigurationService {

	private final Map<String, MatchingStrategy> strategiesByName;
	private final MatchingConfig currentConfig = new MatchingConfig();

	public MatchingConfigurationService(List<MatchingStrategy> strategies) {
		this.strategiesByName = strategies.stream()
				.collect(Collectors.toMap(MatchingStrategy::getName, strategy -> strategy));
	}

	public MatchingConfig getCurrentConfig() {
		// TODO: implement - return the active configuration
		return currentConfig;
	}

	/**
	 * The strategies available to choose from. Populated by Spring, so this one
	 * genuinely works.
	 */
	public List<String> getAvailableStrategies() {
		return List.copyOf(strategiesByName.keySet());
	}

	public void setStrategy(String strategyName) {
		// TODO: implement - look the strategy up by name and reject unknown names
	}

	public void updateCriteria(List<MatchingCriterion> criteria) {
		// TODO: implement
	}
}
