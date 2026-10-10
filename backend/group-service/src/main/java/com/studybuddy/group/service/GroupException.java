package com.studybuddy.group.service;

import org.springframework.http.HttpStatus;

public class GroupException extends RuntimeException {
    private final HttpStatus status;

    public GroupException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
