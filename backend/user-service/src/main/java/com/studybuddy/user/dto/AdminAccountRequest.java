package com.studybuddy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminAccountRequest(@Email @NotBlank String email, @NotBlank @Size(max = 100) String name,
		@NotBlank @Size(min = 8) String password, @NotBlank String role,
		@Size(max = 200) String school, @Size(max = 200) String programme,
		@Min(1) @Max(5) Integer yearOfStudy) { }
