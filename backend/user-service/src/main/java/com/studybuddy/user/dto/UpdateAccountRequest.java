package com.studybuddy.user.dto;

import jakarta.validation.constraints.Email;

public record UpdateAccountRequest(@Email String email, String name, String school) { }
