package com.studybuddy.user.controller;

import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.User;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.dto.AccountResponse;
import com.studybuddy.user.dto.AdminAccountRequest;
import com.studybuddy.user.dto.UpdateAccountRequest;
import jakarta.validation.Valid;
import com.studybuddy.user.service.UserAccountService;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
 * These routes are restricted to authenticated system administrators.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

	private final UserAccountService userAccountService;

	public AdminController(UserAccountService userAccountService) {
		this.userAccountService = userAccountService;
	}

	@GetMapping("/accounts")
	public List<AccountResponse> findAllAccounts() {
		return userAccountService.findAll().stream().map(AccountResponse::from).toList();
	}

	@GetMapping("/accounts/{userId}")
	public AccountResponse findAccount(@PathVariable String userId) {
		return AccountResponse.from(userAccountService.findById(userId));
	}

	@org.springframework.web.bind.annotation.PostMapping("/accounts")
	public ResponseEntity<AccountResponse> createAccount(@Valid @org.springframework.web.bind.annotation.RequestBody AdminAccountRequest request) {
		User account;
		if ("ADMIN".equalsIgnoreCase(request.role())) account = new SystemAdministrator(null, request.email(), request.password(), request.name());
		else if ("STUDENT".equalsIgnoreCase(request.role())) {
			Student student = new Student(null, request.email(), request.password(), AccountStatus.ACTIVE);
			student.setName(request.name());
			student.setSchool(request.school() == null || request.school().isBlank() ? "Undeclared" : request.school());
			student.setProgramme(request.programme() == null || request.programme().isBlank()
					? "Undeclared" : request.programme());
			student.setYearOfStudy(request.yearOfStudy() == null ? 1 : request.yearOfStudy());
			account = student;
		} else throw new IllegalArgumentException("role must be STUDENT or ADMIN");
		return ResponseEntity.status(HttpStatus.CREATED).body(AccountResponse.from(userAccountService.createUserAccount(account)));
	}

	@org.springframework.web.bind.annotation.PutMapping("/accounts/{userId}")
	public AccountResponse updateAccount(@PathVariable String userId,
			@Valid @org.springframework.web.bind.annotation.RequestBody UpdateAccountRequest request) {
		return AccountResponse.from(userAccountService.updateUserAccount(userId, request.email(), request.name(),
				request.school(), request.programme(), request.yearOfStudy()));
	}

	@GetMapping("/accounts/{userId}/status")
	public AccountStatus getAccountStatus(@PathVariable String userId) {
		return userAccountService.viewAccountStatus(userId);
	}

	@PutMapping("/accounts/{userId}/status")
	public AccountResponse setAccountStatus(@PathVariable String userId,
									@RequestParam AccountStatus status) {
		return AccountResponse.from(userAccountService.setAccountStatus(userId, status));
	}

	@DeleteMapping("/accounts/{userId}")
	public ResponseEntity<Void> deleteAccount(@PathVariable String userId) {
		userAccountService.deleteUserAccount(userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/usage")
	public Map<String, Object> getUsageSummary() {
		return userAccountService.getUsageSummary();
	}
}
