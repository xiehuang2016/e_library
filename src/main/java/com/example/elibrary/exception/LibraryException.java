package com.example.elibrary.exception;

import org.springframework.http.HttpStatus;

public class LibraryException extends RuntimeException {
    private final HttpStatus status;

    public LibraryException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
