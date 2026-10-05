package com.studybuddy.user.dto;

public record AuthResponse(String accessToken, String tokenType, AccountResponse user) { }
