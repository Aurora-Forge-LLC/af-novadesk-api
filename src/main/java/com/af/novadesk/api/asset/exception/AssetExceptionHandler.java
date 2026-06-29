package com.af.novadesk.api.asset.exception;

import com.af.novadesk.api.common.exception.EmployeeNotFoundException;
import com.af.novadesk.api.common.exception.OutboxPublishException;
import com.af.novadesk.api.finance.exception.AttachmentNotFoundException;
import com.af.novadesk.api.finance.exception.BadRequestException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Order(4)
@RestControllerAdvice(basePackages = "com.af.novadesk.api.asset")
public class AssetExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AssetExceptionHandler.class);

    public static class ErrorResponse {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final Object details;
        private final String path;
        private final LocalDateTime timestamp;

        public ErrorResponse(boolean success, String errorCode, String message, Object details, String path, LocalDateTime timestamp) {
            this.success = success;
            this.errorCode = errorCode;
            this.message = message;
            this.details = details;
            this.path = path;
            this.timestamp = timestamp;
        }

        static ErrorResponse of(String errorCode, String message, String path) {
            return new ErrorResponse(false, errorCode, message, null, path, LocalDateTime.now());
        }

        static ErrorResponse of(String errorCode, String message, Object details, String path) {
            return new ErrorResponse(false, errorCode, message, details, path, LocalDateTime.now());
        }

        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public Object getDetails() { return details; }
        public String getPath() { return path; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    // =========================================================================
    // Asset domain exceptions
    // =========================================================================

    @ExceptionHandler(AssetNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAssetNotFound(
            AssetNotFoundException ex, HttpServletRequest req) {
        log.warn("Asset not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateSerialNumberException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateSerialNumber(
            DuplicateSerialNumberException ex, HttpServletRequest req) {
        log.warn("Duplicate serial number: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidAssetStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAssetState(
            InvalidAssetStateException ex, HttpServletRequest req) {
        log.warn("Invalid asset state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Finance exceptions used by asset services
    // =========================================================================

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAttachmentNotFound(
            AttachmentNotFoundException ex, HttpServletRequest req) {
        log.warn("Attachment not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest req) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("BAD_REQUEST", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEmployeeNotFound(
            EmployeeNotFoundException ex, HttpServletRequest req) {
        log.warn("Employee not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Outbox
    // =========================================================================

    @ExceptionHandler(OutboxPublishException.class)
    public ResponseEntity<ErrorResponse> handleOutboxFailure(
            OutboxPublishException ex, HttpServletRequest req) {
        log.error("Outbox publish failure — rolling back transaction: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ex.getErrorCode(),
                        "An internal error occurred. Please try again.",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Security
    // =========================================================================

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthorizationDenied(
            AuthorizationDeniedException ex, HttpServletRequest req) {
        log.warn("Access denied on {}: {}", req.getRequestURI(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of("ACCESS_DENIED",
                        "You do not have permission to perform this action.",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Data integrity
    // =========================================================================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("DATA_INTEGRITY",
                        "A record with the same key already exists.",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Spring MVC validation & deserialization
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Request validation failed",
                        fieldErrors, req.getRequestURI()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR",
                        "Malformed request body: " + detail,
                        req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String detail = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", detail, req.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> handleMissingParam(
            MissingServletRequestParameterException ex, HttpServletRequest req) {
        Map<String, String[]> paramMap = req.getParameterMap();
        log.warn("Missing required request parameter '{}' (type {}) on {} {}",
                ex.getParameterName(), ex.getParameterType(),
                req.getMethod(), req.getRequestURI());
        log.warn("Query string: {}", req.getQueryString() != null ? req.getQueryString() : "(none)");
        log.warn("Parameter names received: {}",
                paramMap.isEmpty() ? "(none)" : String.join(", ", paramMap.keySet()));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("MISSING_PARAM",
                        "Required request parameter '" + ex.getParameterName() + "' is not present",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Multipart / file upload
    // =========================================================================

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(
            MultipartException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR",
                        "Expected a multipart/form-data request with a 'file' part.",
                        req.getRequestURI()));
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<ErrorResponse> handleMissingPart(
            MissingServletRequestPartException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR",
                        "Required request part '" + ex.getRequestPartName() + "' is missing",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Infrastructure
    // =========================================================================

    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleNotImplemented(
            UnsupportedOperationException ex, HttpServletRequest req) {
        log.warn("Not implemented: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ErrorResponse.of("NOT_IMPLEMENTED",
                        ex.getMessage() != null ? ex.getMessage() : "This endpoint is not yet implemented",
                        req.getRequestURI()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        log.error("Unexpected internal state on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR",
                        "An unexpected internal error occurred. Please contact support.",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Catch-all
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest req) {
        log.error("Unexpected error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again.",
                        req.getRequestURI()));
    }
}
