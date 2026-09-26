package com.studybuddy.user.service;

import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.User;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * Account management for every kind of user.
 *
 * This is where the six methods the UML puts on SystemAdministrator actually
 * live. Working against the User base type means one implementation covers
 * students and administrators alike.
 */
@Service
public class UserAccountService {

	private final UserRepository userRepository;

	public UserAccountService(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	public List<User> findAll() {
		// TODO: implement
		return List.of();
	}

	public User findById(String userId) {
		// TODO: implement - decide how a missing record is reported (the team has
		// not yet agreed an exception + @RestControllerAdvice approach)
		return null;
	}

	public User create(User user) {
		// TODO: implement
		return null;
	}

	public void delete(String userId) {
		// TODO: implement
	}

	public AccountStatus getAccountStatus(String userId) {
		// TODO: implement
		return null;
	}

	public User changeAccountStatus(String userId, AccountStatus status) {
		// TODO: implement
		return null;
	}

	/**
	 * Basic usage information for the administrator dashboard.
	 */
	public Map<String, Object> getUsageSummary() {
		// TODO: implement
		return Map.of();
	}
}
