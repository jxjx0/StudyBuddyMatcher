package com.studybuddy.user.controller;

import com.studybuddy.user.dto.AuthResponse;
import com.studybuddy.user.dto.LoginRequest;
import com.studybuddy.user.dto.RegisterRequest;
import com.studybuddy.user.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
	private final AuthService authService;
	public AuthController(AuthService authService) { this.authService = authService; }
	@PostMapping("/login")
	public AuthResponse login(@Valid @RequestBody LoginRequest request) { return authService.login(request); }
	@PostMapping("/register")
	public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
	}
	@GetMapping("/me")
	public Map<String, String> me(@AuthenticationPrincipal Jwt jwt) {
		return Map.of("userId", jwt.getClaimAsString("uid"), "email", jwt.getSubject(),
				"role", jwt.getClaimAsStringList("roles").getFirst().replace("ROLE_", ""));
	}
}
