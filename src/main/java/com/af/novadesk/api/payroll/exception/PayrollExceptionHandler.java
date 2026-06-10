package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.common.exception.AuthHubIntegrationException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.common.exception.EmployeeNotFoundException;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception → HTTP response mapping for the Payroll module.
 *
 * <p>Mirrors {@code FinanceExceptionHandler} pattern. Translates payroll domain
 * exceptions and standard Spring MVC exceptions into a consistent error envelope.</p>
 */
@Order(1)
@RestControllerAdvice(basePackages = "com.af.novadesk.api.payroll")
public class PayrollExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PayrollExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Error envelope
    // -------------------------------------------------------------------------

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
    // Employee exceptions
    // =========================================================================

    @ExceptionHandler(EmployeeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEmployeeNotFound(
            EmployeeNotFoundException ex, HttpServletRequest req) {
        log.warn("Employee not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateEmployeeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmployee(
            DuplicateEmployeeException ex, HttpServletRequest req) {
        log.warn("Duplicate employee: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(AuthHubIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleAuthHubIntegration(
            AuthHubIntegrationException ex, HttpServletRequest req) {
        log.error("AuthHub integration failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Leave Request exceptions
    // =========================================================================

    @ExceptionHandler(LeaveRequestNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLeaveRequestNotFound(
            LeaveRequestNotFoundException ex, HttpServletRequest req) {
        log.warn("Leave request not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(LeaveBalanceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleLeaveBalanceNotFound(
            LeaveBalanceNotFoundException ex, HttpServletRequest req) {
        log.warn("Leave balance not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(LeaveBalanceInsufficientException.class)
    public ResponseEntity<ErrorResponse> handleLeaveBalanceInsufficient(
            LeaveBalanceInsufficientException ex, HttpServletRequest req) {
        log.warn("Insufficient leave balance: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidLeaveStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLeaveState(
            InvalidLeaveStateException ex, HttpServletRequest req) {
        log.warn("Invalid leave state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidLeaveDateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidLeaveDate(
            InvalidLeaveDateException ex, HttpServletRequest req) {
        log.warn("Invalid leave date: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Payroll Batch exceptions
    // =========================================================================

    @ExceptionHandler(PayrollBatchNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePayrollBatchNotFound(
            PayrollBatchNotFoundException ex, HttpServletRequest req) {
        log.warn("Payroll batch not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(PayrollBatchAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handlePayrollBatchAlreadyExists(
            PayrollBatchAlreadyExistsException ex, HttpServletRequest req) {
        log.warn("Duplicate payroll batch: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidPayrollStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidPayrollState(
            InvalidPayrollStateException ex, HttpServletRequest req) {
        log.warn("Invalid payroll state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(PayrollProcessingException.class)
    public ResponseEntity<ErrorResponse> handlePayrollProcessing(
            PayrollProcessingException ex, HttpServletRequest req) {
        log.error("Payroll processing error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(PayrollApprovalException.class)
    public ResponseEntity<ErrorResponse> handlePayrollApproval(
            PayrollApprovalException ex, HttpServletRequest req) {
        log.warn("Payroll approval pre-checks failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(),
                        ex.getPreCheckFailures(), req.getRequestURI()));
    }

    // =========================================================================
    // Payslip exceptions
    // =========================================================================

    @ExceptionHandler(PayslipNotFoundException.class)
    public ResponseEntity<ErrorResponse> handlePayslipNotFound(
            PayslipNotFoundException ex, HttpServletRequest req) {
        log.warn("Payslip not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(PayslipGenerationException.class)
    public ResponseEntity<ErrorResponse> handlePayslipGeneration(
            PayslipGenerationException ex, HttpServletRequest req) {
        log.error("Payslip generation failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(PayrollFlaggedEmployeeNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFlaggedEmployeeNotFound(
            PayrollFlaggedEmployeeNotFoundException ex, HttpServletRequest req) {
        log.warn("Flagged employee not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Tax Configuration exceptions
    // =========================================================================

    @ExceptionHandler(TaxConfigurationNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleTaxConfigurationNotFound(
            TaxConfigurationNotFoundException ex, HttpServletRequest req) {
        log.warn("Tax configuration not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidTaxConfigurationException.class)
    public ResponseEntity<ErrorResponse> handleInvalidTaxConfiguration(
            InvalidTaxConfigurationException ex, HttpServletRequest req) {
        log.warn("Invalid tax configuration: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Ledger exceptions
    // =========================================================================

    @ExceptionHandler(PayrollLedgerValidationException.class)
    public ResponseEntity<ErrorResponse> handlePayrollLedgerValidation(
            PayrollLedgerValidationException ex, HttpServletRequest req) {
        log.warn("Payroll ledger validation failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Spring MVC validation
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

    // =========================================================================
    // Missing request parameter (diagnostic logging)
    // =========================================================================

    /**
     * Handles {@link MissingServletRequestParameterException} with diagnostic logging
     * to help debug parameter name mismatches between frontend and API.
     *
     * <p>Logs the query string, all parameter names, and entity/org headers
     * so we can see exactly what the client is sending.</p>
     */
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
    // Spring MVC deserialization
    // =========================================================================

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
