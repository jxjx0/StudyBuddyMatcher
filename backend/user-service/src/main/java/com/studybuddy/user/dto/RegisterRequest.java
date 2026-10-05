package com.studybuddy.user.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for a student signing up. Same fields as {@link StudentRequest}, but email and
 * password are required here, while an update may leave them out.
 *
 * There is no role field: registration always creates a student, never an admin.
 */
public class RegisterRequest extends StudentRequest {

	@Override
	@NotBlank(message = "email is required")
	public String getEmail() {
		return super.getEmail();
	}

	@Override
	@NotBlank(message = "password is required")
	public String getPassword() {
		return super.getPassword();
	}
}
