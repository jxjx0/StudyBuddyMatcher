package com.studybuddy.user.service;

import com.studybuddy.user.dto.AccountResponse;
import com.studybuddy.user.dto.AuthResponse;
import com.studybuddy.user.dto.LoginRequest;
import com.studybuddy.user.dto.RegisterRequest;
import com.studybuddy.user.model.AccountStatus;
import com.studybuddy.user.repository.UserRepository;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
	private final UserRepository users;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;
	private final StudentService studentService;
	public AuthService(UserRepository users, PasswordEncoder passwordEncoder, JwtService jwtService,
			StudentService studentService) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
		this.studentService = studentService;
	}
	// Always creates a Student, so the token's role is STUDENT; the student is logged in straight away.
	public AuthResponse register(RegisterRequest request) {
		var created = studentService.create(request);
		return response(users.findById(created.getUserId()).orElseThrow());
	}
	public AuthResponse login(LoginRequest request) {
		var user = users.findByEmail(request.email()).orElseThrow(() -> new BadCredentialsException("Invalid email or password"));
		if (user.getAccountStatus() != AccountStatus.ACTIVE || !passwordEncoder.matches(request.password(), user.getPasswordHash()))
			throw new BadCredentialsException("Invalid email or password");
		return response(user);
	}
	private AuthResponse response(com.studybuddy.user.model.User user) {
		return new AuthResponse(jwtService.issue(user), "Bearer", AccountResponse.from(user));
	}
}
