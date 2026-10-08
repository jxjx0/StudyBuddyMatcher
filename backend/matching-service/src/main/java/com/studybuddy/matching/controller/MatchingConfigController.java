package com.studybuddy.matching.controller;

import com.studybuddy.matching.model.MatchingConfig;
import com.studybuddy.matching.model.MatchingCriterion;
import com.studybuddy.matching.service.MatchingConfigurationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

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

	/**
	 * Rejected settings (unknown strategy, weights not totalling 100, a strategy
	 * rule broken) come back as 400 with a message the admin page can show.
	 */
	@ExceptionHandler(IllegalArgumentException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> invalidSettings(IllegalArgumentException exception) {
		return Map.of("message", exception.getMessage());
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> unreadableSettings() {
		return Map.of("message", "Check the criterion types and weights");
	}
}
