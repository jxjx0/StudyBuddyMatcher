package com.studybuddy.user.controller;

import com.studybuddy.user.service.AccountNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {
	@ExceptionHandler(AccountNotFoundException.class)
	@ResponseStatus(HttpStatus.NOT_FOUND)
	public Map<String, String> notFound(RuntimeException exception) { return Map.of("message", exception.getMessage()); }
	@ExceptionHandler({IllegalArgumentException.class, DataIntegrityViolationException.class})
	@ResponseStatus(HttpStatus.CONFLICT)
	public Map<String, String> conflict(RuntimeException exception) { return Map.of("message", exception.getMessage()); }
	@ExceptionHandler(BadCredentialsException.class)
	@ResponseStatus(HttpStatus.UNAUTHORIZED)
	public Map<String, String> unauthorized() { return Map.of("message", "Invalid email or password"); }
	@ExceptionHandler(MethodArgumentNotValidException.class)
	@ResponseStatus(HttpStatus.BAD_REQUEST)
	public Map<String, String> invalid(MethodArgumentNotValidException exception) {
		String message = exception.getBindingResult().getFieldErrors().stream().findFirst()
				.map(error -> error.getDefaultMessage()).orElse("Request validation failed");
		return Map.of("message", message);
	}
}
