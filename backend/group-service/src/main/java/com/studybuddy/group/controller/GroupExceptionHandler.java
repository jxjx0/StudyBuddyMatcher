package com.studybuddy.group.controller;

import com.studybuddy.group.service.GroupException;
import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestControllerAdvice
public class GroupExceptionHandler {
    @ExceptionHandler(GroupException.class)
    ResponseEntity<?> group(GroupException e) { return error(e.getStatus(), e.getMessage()); }
    @ExceptionHandler(IllegalStateException.class)
    ResponseEntity<?> state(IllegalStateException e) { return error(HttpStatus.CONFLICT, e.getMessage()); }
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<?> argument(IllegalArgumentException e) { return error(HttpStatus.BAD_REQUEST, e.getMessage()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        var field = e.getBindingResult().getFieldErrors().getFirst();
        return error(HttpStatus.BAD_REQUEST, field.getField() + ": " + field.getDefaultMessage());
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<?> malformed(Exception e) { return error(HttpStatus.BAD_REQUEST, "Check the supplied values and identifiers"); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(DataIntegrityViolationException e) { return error(HttpStatus.CONFLICT, "This operation conflicts with the current group or request. Refresh and try again."); }
    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<?> database(DataAccessException e) { return error(HttpStatus.SERVICE_UNAVAILABLE, "Group storage is unavailable. Please try again shortly."); }
    private ResponseEntity<?> error(HttpStatus status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }
}
