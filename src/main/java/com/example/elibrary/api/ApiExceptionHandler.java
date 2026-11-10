package com.example.elibrary.api;

import com.example.elibrary.exception.LibraryException;
import com.example.elibrary.logging.ScopeValue;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.ConstraintViolationException;
import java.time.Instant;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(LibraryException.class)
    public ResponseEntity<ApiError> library(LibraryException exception) {
        log.warn("request_failed status={} exceptionType={}",
                exception.getStatus().value(), exception.getClass().getSimpleName());
        return ResponseEntity.status(exception.getStatus())
                .body(new ApiError(exception.getStatus().value(), exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, ConstraintViolationException.class})
    public ResponseEntity<ApiError> validation(Exception exception) {
        log.warn("request_validation_failed");
        return ResponseEntity.badRequest()
                .body(new ApiError(400, "Invalid request: check required fields and values"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception exception) {
        log.error("unhandled_request_failure", exception);
        return ResponseEntity.internalServerError()
                .body(new ApiError(500, "Internal server error"));
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiError> concurrentUpdate(ObjectOptimisticLockingFailureException exception) {
        log.warn("optimistic_lock_conflict", exception);
        return ResponseEntity.status(409)
                .body(new ApiError(409, "The record changed concurrently; reload and retry"));
    }

    public static class ApiError {
        private final Instant timestamp = Instant.now();
        private final int status;
        private final String message;
        private final String requestId = ScopeValue.get(ScopeValue.REQUEST_ID).orElse(null);

        ApiError(int status, String message) {
            this.status = status;
            this.message = message;
        }

        public Instant getTimestamp() {
            return timestamp;
        }

        public int getStatus() {
            return status;
        }

        public String getMessage() {
            return message;
        }

        public String getRequestId() {
            return requestId;
        }
    }
}
