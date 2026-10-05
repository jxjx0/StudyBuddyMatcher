package com.studybuddy.user.controller;

import com.studybuddy.user.dto.StudentContactDto;
import com.studybuddy.user.dto.StudentDto;
import com.studybuddy.user.dto.StudentRequest;
import com.studybuddy.user.service.StudentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;

/**
 * Student profile endpoints.
 *
 * This controller is the shape the other services follow: it maps HTTP to
 * method calls and validates input, and does nothing else. All logic sits in
 * the service layer.
 */
@RestController
@RequestMapping("/api/students")
public class StudentController {

	private final StudentService studentService;

	public StudentController(StudentService studentService) {
		this.studentService = studentService;
	}

	@GetMapping
	public List<StudentDto> findAll() {
		return studentService.findAll();
	}

	@GetMapping("/{userId}")
	public StudentDto findById(@PathVariable String userId) {
		return studentService.findById(userId);
	}

	/**
	 * Contact details for a matched student. Called by connection-service once
	 * a match request has been accepted, not by the browser.
	 *
	 * A student can read their own number; administrators can assist with account
	 * management. Connection-service integration can add accepted-match access.
	 */
	@GetMapping("/{userId}/contact")
	public StudentContactDto findContactDetails(@PathVariable String userId, @AuthenticationPrincipal Jwt jwt) {
		if (!isAdmin(jwt) && !userId.equals(jwt.getClaimAsString("uid"))) {
			throw new AccessDeniedException("You can only view your own contact details");
		}
		return studentService.findContactDetails(userId);
	}

	// POST /api/students
	@PostMapping
	// ResponseEntity<T> wraps the whole HTTP response: the status code, the headers and the body.
	// @RequestBody tells Spring to read the JSON body of the request and turn it into a Java object (StudentRequest).

	// @Valid runs the validation rules defined on the fields of StudentRequest, like @NotBlank, @Email or @Size.
	// If a check fails, Spring rejects the request with 400 Bad Request before your method even runs.

	public ResponseEntity<StudentDto> create(@Valid @RequestBody StudentRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(studentService.create(request));
	}

	@PutMapping("/{userId}")
	// @AuthenticationPrincipal Jwt jwt gives you the logged-in user. Spring Security has already checked the
	// JWT token from the Authorization: Bearer ... header, and this annotation passes the decoded token into your method
	public StudentDto update(@PathVariable String userId,
							 @Valid @RequestBody StudentRequest request, @AuthenticationPrincipal Jwt jwt) {
		// jwt.getClaimAsString("uid") reads the uid value from the logged-in user's token,
		// which is the ID of whoever sent the request.
		if (!isAdmin(jwt) && !userId.equals(jwt.getClaimAsString("uid"))) {
			throw new AccessDeniedException("You can only update your own profile");
		}
		return studentService.update(userId, request);
	}

	// A helper method to check whether user is an admin
	private boolean isAdmin(Jwt jwt) {
		return jwt.getClaimAsStringList("roles").contains("ROLE_ADMIN");
	}
}
