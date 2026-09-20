package com.studybuddy.matching.controller;

import com.studybuddy.matching.model.MatchingConfig;
import com.studybuddy.matching.model.MatchingCriterion;
import com.studybuddy.matching.service.MatchingConfigurationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Administrator control over how matching behaves.
 *
 * Routed under /api/matching rather than /api/admin because these endpoints
 * live in matching-service - the gateway sends /api/admin to user-service.
 */
@RestController
@RequestMapping("/api/matching/config")
public class MatchingConfigController {

	private final MatchingConfigurationService configService;

	public MatchingConfigController(MatchingConfigurationService configService) {
		this.configService = configService;
	}

	@GetMapping
	public MatchingConfig getCurrentConfig() {
		return configService.getCurrentConfig();
	}

	@GetMapping("/strategies")
	public List<String> getAvailableStrategies() {
		return configService.getAvailableStrategies();
	}

	@PutMapping("/strategy")
	public void setStrategy(@RequestParam String name) {
		configService.setStrategy(name);
	}

	@PutMapping("/criteria")
	public void updateCriteria(@RequestBody List<MatchingCriterion> criteria) {
		configService.updateCriteria(criteria);
	}
}
