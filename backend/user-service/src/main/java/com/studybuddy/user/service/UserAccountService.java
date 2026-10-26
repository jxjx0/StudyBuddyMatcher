package com.studybuddy.user.service;

import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.model.Student;
import com.studybuddy.user.model.SystemAdministrator;
import com.studybuddy.user.model.User;
import com.studybuddy.user.repository.StudentRepository;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAccountService {
	private final UserRepository userRepository;
	private final StudentRepository studentRepository;
	private final PasswordEncoder passwordEncoder;
	public UserAccountService(UserRepository userRepository, StudentRepository studentRepository,
			PasswordEncoder passwordEncoder) {
		this.userRepository = userRepository;
		this.studentRepository = studentRepository;
		this.passwordEncoder = passwordEncoder;
	}
	public List<User> findAll() { return userRepository.findAll(); }
	public User findById(String userId) {
		return userRepository.findById(userId).orElseThrow(() -> new AccountNotFoundException("Account not found"));
	}
	@Transactional
	public User create(User user) {
		if (userRepository.findByEmail(user.getEmail()).isPresent()) throw new IllegalArgumentException("Email is already registered");
		if (user.getPasswordHash() == null || user.getPasswordHash().length() < 8) throw new IllegalArgumentException("Password must contain at least 8 characters");
		user.setPasswordHash(passwordEncoder.encode(user.getPasswordHash()));
		User created = userRepository.save(user);
		if (created instanceof Student student) studentRepository.save(student);
		return created;
	}
	public User createUserAccount(User user) { return create(user); }
	@Transactional
	public User update(String userId, String email, String name, String school, String programme, Integer yearOfStudy) {
		User user = findById(userId);
		if (email != null && !email.isBlank()) {
			userRepository.findByEmail(email).filter(other -> !other.getUserId().equals(userId))
					.ifPresent(other -> { throw new IllegalArgumentException("Email is already registered"); });
			user.setEmail(email);
		}
		if (user instanceof Student student) {
			Student profile = findOrCreateStudentProfile(student);
			profile.setEmail(user.getEmail());
			profile.setPasswordHash(user.getPasswordHash());
			profile.setAccountStatus(user.getAccountStatus());
			if (name != null) profile.setName(name);
			if (school != null) profile.setSchool(school);
			if (programme != null) profile.setProgramme(programme);
			if (yearOfStudy != null) profile.setYearOfStudy(yearOfStudy);
			userRepository.save(profile);
			if (!studentRepository.update(profile))
				throw new AccountNotFoundException("Student profile could not be updated");
			return profile;
		} else if (name != null && user instanceof SystemAdministrator admin) admin.setName(name);
		return userRepository.save(user);
	}
	public User updateUserAccount(String userId, String email, String name, String school,
			String programme, Integer yearOfStudy) {
		return update(userId, email, name, school, programme, yearOfStudy);
	}

	private Student findOrCreateStudentProfile(Student account) {
		return studentRepository.findById(account.getUserId()).orElseGet(() -> {
			if (account.getName() == null || account.getName().isBlank() || account.getName().length() > 100)
				account.setName("Student");
			if (account.getSchool() == null || account.getSchool().isBlank() || account.getSchool().length() > 200)
				account.setSchool("Undeclared");
			if (account.getProgramme() == null || account.getProgramme().isBlank()
					|| account.getProgramme().length() > 200)
				account.setProgramme("Undeclared");
			if (account.getYearOfStudy() < 1 || account.getYearOfStudy() > 5)
				account.setYearOfStudy(1);
			studentRepository.saveIfAbsent(account);
			return studentRepository.findById(account.getUserId())
					.orElseThrow(() -> new AccountNotFoundException("Student profile could not be created"));
		});
	}
	public void delete(String userId) {
		if (!userRepository.deleteById(userId)) throw new AccountNotFoundException("Account not found");
	}
	public void deleteUserAccount(String userId) { delete(userId); }
	public AccountStatus getAccountStatus(String userId) { return findById(userId).getAccountStatus(); }
	public AccountStatus viewAccountStatus(String userId) { return getAccountStatus(userId); }
	public User setAccountStatus(String userId, AccountStatus status) {
		User user = findById(userId);
		user.setAccountStatus(status);
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
