package com.studybuddy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountRequest(@Email @NotBlank String email, @NotBlank String name,
		@NotBlank @Size(min = 8) String password, @NotBlank String role, String school) { }
