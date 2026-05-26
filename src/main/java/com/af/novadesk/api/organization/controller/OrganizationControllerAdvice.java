package com.af.novadesk.api.organization.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.exception.DuplicateEntityException;
import com.af.novadesk.api.finance.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Centralised exception handler for the organization controller slice.
 *
 * <p>Handles exceptions thrown by organization endpoints and returns
 * standard {@link ApiResponse} envelopes consistent with the rest of the API.</p>
 */
@RestControllerAdvice(basePackages = "com.af.novadesk.api.organization.controller")
public class OrganizationControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(OrganizationControllerAdvice.class);

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NotFoundException ex) {
        log.warn("Organization resource not found: {}", ex.getMessage());
        return ResponseBuilder.notFound(ex.getMessage());
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateEntityException ex) {
        log.warn("Duplicate organization resource: {}", ex.getMessage());
        return ResponseBuilder.conflict(ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        log.error("Illegal state in organization module: {}", ex.getMessage());
        return ResponseBuilder.badRequest(ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validation failed: {}", details);
        return ResponseBuilder.badRequest(ApiMessages.VALIDATION_FAILED + " - " + details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unexpected error in organization module", ex);
        return ResponseBuilder.internalServerError(ApiMessages.INTERNAL_SERVER_ERROR);
    }
}
