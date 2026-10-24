package com.studybuddy.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateAccountRequest(@Email String email, @Size(max = 100) String name,
		@Size(max = 200) String school, @Size(max = 200) String programme,
		@Min(1) @Max(5) Integer yearOfStudy) { }
