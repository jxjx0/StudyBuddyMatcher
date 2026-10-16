package com.studybuddy.matching.service;

import com.studybuddy.matching.model.MatchingConfig;
import com.studybuddy.matching.model.MatchingCriterion;
import com.studybuddy.matching.model.CriterionType;
import com.studybuddy.matching.engine.MatchingStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
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
public class MatchingConfigurationService implements CommandLineRunner {

	private static final Logger log = LoggerFactory.getLogger(MatchingConfigurationService.class);

	private final Map<String, MatchingStrategy> strategiesByName;
	private final JdbcTemplate jdbc;
	private final MatchingConfig currentConfig = new MatchingConfig();

	public MatchingConfigurationService(List<MatchingStrategy> strategies, JdbcTemplate jdbc) {
		this.strategiesByName = strategies.stream()
				.collect(Collectors.toMap(strategy -> strategy.getName().toUpperCase(), strategy -> strategy));
		this.jdbc = jdbc;
		this.currentConfig.setCriteria(List.of(
				new MatchingCriterion(CriterionType.COURSE, 20, true),
				new MatchingCriterion(CriterionType.AVAILABILITY, 20, true),
				new MatchingCriterion(CriterionType.STUDY_MODE, 20, true),
				new MatchingCriterion(CriterionType.STUDY_GOAL, 20, true),
				new MatchingCriterion(CriterionType.GROUP_SIZE, 20, true)));
	}

	@Override
	public void run(String... args) {
		String strategyName = jdbc.queryForObject("SELECT strategy_name FROM matching_configuration WHERE config_id = 1", String.class);
		currentConfig.setStrategy(strategyName == null ? null : requireStrategy(strategyName));
		List<MatchingCriterion> savedCriteria = jdbc.query("SELECT criterion_type, weight, enabled FROM matching_criteria ORDER BY criterion_type",
				(rs, row) -> new MatchingCriterion(CriterionType.valueOf(rs.getString("criterion_type")),
						rs.getDouble("weight"), rs.getBoolean("enabled")));
		if (!savedCriteria.isEmpty()) currentConfig.setCriteria(savedCriteria);
		// Weights saved before strategies enforced their rules may not follow them. Searches still work
		// (MatchingEngine falls back to these weights), but the administrator should save them again.
		MatchingStrategy strategy = currentConfig.getStrategy();
		if (strategy != null) {
			try {
				strategy.applyStrategy(currentConfig.getCriteria());
			} catch (IllegalArgumentException e) {
				log.warn("Saved matching weights do not follow the {} rule: {}. Re-save them on the admin Matching page.",
						strategy.getName(), e.getMessage());
			}
		}
	}

	public MatchingConfig getCurrentConfig() {
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
		String normalized = strategyName == null ? "" : strategyName.trim().toUpperCase();
		MatchingStrategy selected = normalized.isEmpty() ? null : requireStrategy(normalized);
		jdbc.update("UPDATE matching_configuration SET strategy_name = ? WHERE config_id = 1",
				selected == null ? null : selected.getName());
		currentConfig.setStrategy(selected);
	}

	@Transactional
	public void updateCriteria(List<MatchingCriterion> criteria) {
		if (criteria == null || criteria.isEmpty()) throw new IllegalArgumentException("At least one criterion is required");
		EnumSet<CriterionType> seen = EnumSet.noneOf(CriterionType.class);
		double enabledWeight = 0;
		for (MatchingCriterion criterion : criteria) {
			if (criterion == null || criterion.getType() == null) throw new IllegalArgumentException("Each criterion needs a type");
			if (!seen.add(criterion.getType())) throw new IllegalArgumentException("Duplicate criterion: " + criterion.getType());
			if (!Double.isFinite(criterion.getWeight()) || criterion.getWeight() < 0 || criterion.getWeight() > 100)
				throw new IllegalArgumentException("Criterion weights must be between 0 and 100");
			if (criterion.isEnabled()) enabledWeight += criterion.getWeight();
		}
		if (criteria.stream().noneMatch(MatchingCriterion::isEnabled) || Math.abs(enabledWeight - 100) > 0.01)
			throw new IllegalArgumentException("Enabled criterion weights must total 100");
		// The selected strategy checks its rule here, so weights that break it are rejected before
		// anything is saved. Balanced replaces the weights with equal shares, and those are what get stored.
		MatchingStrategy strategy = currentConfig.getStrategy();
		List<MatchingCriterion> finalCriteria = strategy == null ? criteria : strategy.applyStrategy(criteria);
		jdbc.update("DELETE FROM matching_criteria");
		for (MatchingCriterion criterion : finalCriteria) jdbc.update(
				"INSERT INTO matching_criteria(criterion_type, weight, enabled) VALUES (?, ?, ?)",
				criterion.getType().name(), criterion.getWeight(), criterion.isEnabled());
		currentConfig.setCriteria(List.copyOf(finalCriteria));
	}

	private MatchingStrategy requireStrategy(String name) {
		MatchingStrategy strategy = strategiesByName.get(name.toUpperCase());
		if (strategy == null) throw new IllegalArgumentException("Unknown matching strategy: " + name);
		return strategy;
	}
}
