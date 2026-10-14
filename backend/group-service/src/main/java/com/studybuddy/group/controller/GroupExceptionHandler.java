package com.studybuddy.group.controller;

import com.studybuddy.group.service.GroupException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GroupExceptionHandler {
  @ExceptionHandler(GroupException.class)
  ResponseEntity<?> handleGroupError(GroupException exception) {
    return error(exception.getStatus(), exception.getMessage());
  }

  @ExceptionHandler(IllegalStateException.class)
  ResponseEntity<?> handleInvalidState(IllegalStateException exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage());
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> handleInvalidArgument(IllegalArgumentException exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage());
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> handleValidationError(MethodArgumentNotValidException exception) {
    var fieldError = exception.getBindingResult().getFieldErrors().getFirst();
    String message = fieldError.getField() + ": " + fieldError.getDefaultMessage();
    return error(HttpStatus.BAD_REQUEST, message);
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> handleMalformedRequest(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, "Check the supplied values and identifiers");
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> handleStorageConflict(DataIntegrityViolationException exception) {
    return error(
        HttpStatus.CONFLICT,
        "This operation conflicts with the current group or request. Refresh and try again.");
  }

  @ExceptionHandler(DataAccessException.class)
  ResponseEntity<?> handleUnavailableStorage(DataAccessException exception) {
    return error(
        HttpStatus.SERVICE_UNAVAILABLE, "Group storage is unavailable. Please try again shortly.");
  }

  private ResponseEntity<?> error(HttpStatus status, String message) {
    return ResponseEntity.status(status).body(Map.of("message", message));
  }
}
