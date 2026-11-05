package com.af.novadesk.api.maintenance.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.LocalDateTime;

@Order(4)
@RestControllerAdvice(basePackages = "com.af.novadesk.api.maintenance")
public class MaintenanceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceExceptionHandler.class);

    public static class ErrorResponse {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final String path;
        private final LocalDateTime timestamp;

        public ErrorResponse(boolean success, String errorCode, String message, String path, LocalDateTime timestamp) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.path = path;
            this.timestamp = timestamp;
        }

        static ErrorResponse of(String errorCode, String message, String path) {
            return new ErrorResponse(false, errorCode, message, path, LocalDateTime.now());
        }

        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public String getPath() { return path; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    @ExceptionHandler(MaintenanceRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            MaintenanceRequestNotFoundException ex, HttpServletRequest req) {
        log.warn("Maintenance request not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidMaintenanceStatusTransitionException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTransition(
            InvalidMaintenanceStatusTransitionException ex, HttpServletRequest req) {
        log.warn("Invalid maintenance status transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }
}
