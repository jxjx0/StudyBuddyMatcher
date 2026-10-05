package com.studybuddy.user.service;

import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.model.User;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAccountService {
	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	public UserAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
	}
	public List<User> findAll() { return userRepository.findAll(); }
	public User findById(String userId) {
		return userRepository.findById(userId).orElseThrow(() -> new AccountNotFoundException("Account not found"));
	}
	public User create(User user) {
		if (userRepository.findByEmail(user.getEmail()).isPresent()) throw new IllegalArgumentException("Email is already registered");
		if (user.getPasswordHash() == null || user.getPasswordHash().length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters");
		user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
		return userRepository.save(user);
	}
	public User createUserAccount(User user) { return create(user); }
	public User update(String userId, String email, String name, String school) {
		User user = findById(userId);
		if (email != null && !email.isBlank()) {
			userRepository.findByEmail(email).filter(other -> !other.getUserId().equals(userId))
					.ifPresent(other -> { throw new IllegalArgumentException("Email is already registered"); });
			user.setEmail(email);
		}
		if (user instanceof Student student) {
			if (name != null) student.setName(name);
			if (school != null) student.setSchool(school);
		} else if (name != null && user instanceof SystemAdministrator admin) admin.setName(name);
		return userRepository.save(user);
	}
	public User updateUserAccount(String userId, String email, String name, String school) { return update(userId, email, name, school); }
	public void delete(String userId) {
		if (!userRepository.deleteById(userId)) throw new AccountNotFoundException("Account not found");
	}
	public void deleteUserAccount(String userId) { delete(userId); }
	public AccountStatus getAccountStatus(String userId) { return findById(userId).getAccountStatus(); }
	public AccountStatus viewAccountStatus(String userId) { return getAccountStatus(userId); }
	public User changeAccountStatus(String userId, AccountStatus status) {
		User user = findById(userId);
		user.changeAccountStatus(status);
		return userRepository.save(user);
	}
	public Map<String, Object> getUsageSummary() {
		long students = userRepository.countByRole("STUDENT");
		long administrators = userRepository.countByRole("ADMIN");
		return Map.of("totalAccounts", students + administrators, "students", students,
				"activeStudents", userRepository.countByRoleAndStatus("STUDENT", "ACTIVE"),
				"administrators", administrators);
	}
	public static String newUserId() { return UUID.randomUUID().toString(); }
}
