package com.studybuddy.user.controller;

import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.User;
import com.studybuddy.user.service.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Administrator endpoints for managing accounts.
 *
 * TODO: unprotected. Once authentication exists, restrict these to
 * administrators rather than trusting the caller.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final UserAccountService userAccountService;

	public AdminController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@GetMapping("/accounts")
	public List<User> findAllAccounts() {
		return userAccountService.findAll();
	}

	@GetMapping("/accounts/{userId}")
	public User findAccount(@PathVariable String userId) {
		return userAccountService.findById(userId);
	}

	@GetMapping("/accounts/{userId}/status")
	public AccountStatus getAccountStatus(@PathVariable String userId) {
		return userAccountService.getAccountStatus(userId);
	}

	@PutMapping("/accounts/{userId}/status")
	public User changeAccountStatus(@PathVariable String userId,
									@RequestParam AccountStatus status) {
		return userAccountService.changeAccountStatus(userId, status);
	}

	@DeleteMapping("/accounts/{userId}")
	public ResponseEntity<Void> deleteAccount(@PathVariable String userId) {
		userAccountService.delete(userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/usage")
	public Map<String, Object> getUsageSummary() {
		return userAccountService.getUsageSummary();
	}
}
