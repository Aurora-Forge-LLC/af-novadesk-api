package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.finance.dto.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import com.af.novadesk.api.common.exception.DuplicateEmployeeException;
import com.af.novadesk.api.common.exception.OutboxPublishException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception → HTTP response mapping for the Finance module.
 *
 * <p>Translates domain exception classes and standard Spring MVC
 * exceptions into a consistent error envelope so API consumers always receive
 * the same JSON shape regardless of which layer threw the exception.</p>
 *
 * <p>Error response shape (mirrors {@link ApiResponse} but with added fields):
 * <pre>{@code
 * {
 * "success": false,
 * "errorCode": "FIN_ENTITY_001",
 * "message": "Legal entity not found: ...",
 * "path": "/api/v1/legal-entities/...",
 * "timestamp": "2026-01-01T12:00:00"
 * }
 * }</pre>
 * </p>
 *
 * <p><b>Note:</b> Actuator endpoints ({@code /actuator/**}) are excluded from this
 * handler so that Spring Boot Actuator's built-in exception handling and health
 * indicators work correctly.</p>
 */
@Order(2)
@RestControllerAdvice(basePackages = "com.af.novadesk.api.finance")
public class FinanceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(FinanceExceptionHandler.class);

    // -------------------------------------------------------------------------
    // Error envelope (not reusing ApiResponse — errors carry extra fields)
    // -------------------------------------------------------------------------

    public static class ErrorResponse {
        private final boolean success;
        private final String errorCode;
        private final String message;
        private final Object details;       // null for simple errors; validation field map for 400s
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

        // Getters
        public boolean isSuccess() { return success; }
        public String getErrorCode() { return errorCode; }
        public String getMessage() { return message; }
        public Object getDetails() { return details; }
        public String getPath() { return path; }
        public LocalDateTime getTimestamp() { return timestamp; }
    }

    // =========================================================================
    // Finance domain exceptions
    // =========================================================================

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotFound(
            EntityNotFoundException ex, HttpServletRequest req) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateEntityException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEntity(
            DuplicateEntityException ex, HttpServletRequest req) {
        log.warn("Duplicate entity: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidEntityStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidEntityState(
            InvalidEntityStateException ex, HttpServletRequest req) {
        log.warn("Invalid entity state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(EntityAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            EntityAccessDeniedException ex, HttpServletRequest req) {
        log.warn("Entity access denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(UserAccessNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleUserAccessNotFound(
            UserAccessNotFoundException ex, HttpServletRequest req) {
        log.warn("User access not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateUserAccessException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateUserAccess(
            DuplicateUserAccessException ex, HttpServletRequest req) {
        log.warn("Duplicate user access: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(EntityAdminAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleEntityAdminAlreadyExists(
            EntityAdminAlreadyExistsException ex, HttpServletRequest req) {
        log.warn("Entity admin slot already occupied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(EntityRoleNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleEntityRoleNotPermitted(
            EntityRoleNotPermittedException ex, HttpServletRequest req) {
        log.warn("Entity role management not permitted: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(SelfEntityAdminActionException.class)
    public ResponseEntity<ErrorResponse> handleSelfEntityAdminAction(
            SelfEntityAdminActionException ex, HttpServletRequest req) {
        log.warn("Self entity-admin action denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(LegalEntityManagementNotPermittedException.class)
    public ResponseEntity<ErrorResponse> handleLegalEntityManagementNotPermitted(
            LegalEntityManagementNotPermittedException ex, HttpServletRequest req) {
        log.warn("Legal entity management denied: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(FiscalYearSettingNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleFiscalYearNotFound(
            FiscalYearSettingNotFoundException ex, HttpServletRequest req) {
        log.warn("Fiscal year setting not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(ShadowUserNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleShadowUserNotFound(
            ShadowUserNotFoundException ex, HttpServletRequest req) {
        log.warn("Shadow user not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateEmployeeException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateEmployee(
            DuplicateEmployeeException ex, HttpServletRequest req) {
        log.warn("Duplicate employee (AuthHub): {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(com.af.novadesk.api.common.exception.AuthHubIntegrationException.class)
    public ResponseEntity<ErrorResponse> handleAuthHubIntegration(
            com.af.novadesk.api.common.exception.AuthHubIntegrationException ex, HttpServletRequest req) {
        log.error("AuthHub integration failed: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    /**
     * af-authhub explicitly rejected the identity-provisioning call (401/403) —
     * a real authorization decision, not af-authhub being unreachable/broken.
     * Surfaced as 403 with af-authhub's own reason so the frontend can show it
     * directly instead of a generic "server error" message.
     */
    @ExceptionHandler(com.af.novadesk.api.common.exception.AuthHubAccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAuthHubAccessDenied(
            com.af.novadesk.api.common.exception.AuthHubAccessDeniedException ex, HttpServletRequest req) {
        log.warn("AuthHub denied identity provisioning: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(
            NotFoundException ex, HttpServletRequest req) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of("FIN_ACCOUNT_001", ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Account & Capital Injection domain exceptions
    // =========================================================================

    @ExceptionHandler(AccountNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAccountNotFound(
            AccountNotFoundException ex, HttpServletRequest req) {
        log.warn("Account not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(ExchangeRateNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleExchangeRateNotFound(
            ExchangeRateNotFoundException ex, HttpServletRequest req) {
        log.warn("Exchange rate not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(CapitalInjectionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCapitalInjectionNotFound(
            CapitalInjectionNotFoundException ex, HttpServletRequest req) {
        log.warn("Capital injection not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidAccountStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidAccountState(
            InvalidAccountStateException ex, HttpServletRequest req) {
        log.warn("Invalid account state: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(UnbalancedLedgerException.class)
    public ResponseEntity<ErrorResponse> handleUnbalancedLedger(
            UnbalancedLedgerException ex, HttpServletRequest req) {
        log.warn("Unbalanced ledger: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(EntityNotApprovedException.class)
    public ResponseEntity<ErrorResponse> handleEntityNotApproved(
            EntityNotApprovedException ex, HttpServletRequest req) {
        log.warn("Entity not approved: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(
            BadRequestException ex, HttpServletRequest req) {
        log.warn("Bad request: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("FIN_BAD_REQUEST", ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Vendor & Expense domain exceptions
    // =========================================================================

    @ExceptionHandler(VendorNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleVendorNotFound(
            VendorNotFoundException ex, HttpServletRequest req) {
        log.warn("Vendor not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateVendorException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateVendor(
            DuplicateVendorException ex, HttpServletRequest req) {
        log.warn("Duplicate vendor: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(ExpenseTransactionNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleExpenseTransactionNotFound(
            ExpenseTransactionNotFoundException ex, HttpServletRequest req) {
        log.warn("Expense transaction not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(InvalidExpenseStateException.class)
    public ResponseEntity<ErrorResponse> handleInvalidExpenseState(
            InvalidExpenseStateException ex, HttpServletRequest req) {
        log.warn("Invalid expense state transition: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleAttachmentNotFound(
            AttachmentNotFoundException ex, HttpServletRequest req) {
        log.warn("Attachment not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(MissingExchangeRateException.class)
    public ResponseEntity<ErrorResponse> handleMissingExchangeRate(
            MissingExchangeRateException ex, HttpServletRequest req) {
        log.warn("Missing exchange rate: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("FIN_RATE_002", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(ExchangeRateAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleExchangeRateAlreadyExists(
            ExchangeRateAlreadyExistsException ex, HttpServletRequest req) {
        log.warn("Duplicate exchange rate: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(CsvImportException.class)
    public ResponseEntity<ErrorResponse> handleCsvImportFailed(
            CsvImportException ex, HttpServletRequest req) {
        log.warn("CSV import failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of("FIN_RATE_004", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(
            DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Data integrity violation: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of("FIN_DATA_INTEGRITY",
                        "A record with the same key already exists.",
                        req.getRequestURI()));
    }

    @ExceptionHandler(JwtClaimMissingException.class)
    public ResponseEntity<ErrorResponse> handleJwtClaimMissing(
            JwtClaimMissingException ex, HttpServletRequest req) {
        log.warn("JWT claim missing: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("FIN_JWT_001", ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(OutboxPublishException.class)
    public ResponseEntity<ErrorResponse> handleOutboxFailure(
            OutboxPublishException ex, HttpServletRequest req) {
        log.error("Outbox publish failure — rolling back transaction: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of(ex.getErrorCode(), "An internal error occurred. Please try again.",
                        req.getRequestURI()));
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationRequired(
            AuthenticationRequiredException ex, HttpServletRequest req) {
        log.warn("Authentication required: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

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
    // Bank Reconciliation exceptions (LLR-BNK-01)
    // =========================================================================

    private static final String EXPECTED_CSV_FORMAT =
            "Expected CSV format with headers:\n" +
            "  Date,Description,Debit,Credit,Balance\n\n" +
            "Example:\n" +
            "  2026-01-02,Opening Balance,,100000.00,100000.00\n" +
            "  2026-01-03,Wire Transfer - Client A,,25000.00,125000.00\n" +
            "  2026-01-05,Office Supplies Payment,1500.50,,123499.50\n\n" +
            "Column names are auto-detected. Supported date formats: yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy, etc.\n" +
            "Amounts may include commas (e.g. 1,500.50) and optional currency symbols.";

    @ExceptionHandler(StatementNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleStatementNotFound(
            StatementNotFoundException ex, HttpServletRequest req) {
        log.warn("Bank statement not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(DuplicateStatementException.class)
    public ResponseEntity<ErrorResponse> handleDuplicateStatement(
            DuplicateStatementException ex, HttpServletRequest req) {
        log.warn("Duplicate bank statement: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    @ExceptionHandler(StatementParseException.class)
    public ResponseEntity<ErrorResponse> handleStatementParse(
            StatementParseException ex, HttpServletRequest req) {
        log.warn("Bank statement parse failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(),
                        EXPECTED_CSV_FORMAT, req.getRequestURI()));
    }

    @ExceptionHandler(UnsupportedFileTypeException.class)
    public ResponseEntity<ErrorResponse> handleUnsupportedFileType(
            UnsupportedFileTypeException ex, HttpServletRequest req) {
        log.warn("Unsupported bank statement file type: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of(ex.getErrorCode(), ex.getMessage(), req.getRequestURI()));
    }

    // =========================================================================
    // Standard library exceptions
    // =========================================================================

    /**
     * Maps {@code UnsupportedOperationException} (stub controllers, TODO placeholders)
     * to HTTP 501 Not Implemented so consumers can distinguish unimplemented
     * endpoints from genuine server errors.
     */
    @ExceptionHandler(UnsupportedOperationException.class)
    public ResponseEntity<ErrorResponse> handleNotImplemented(
            UnsupportedOperationException ex, HttpServletRequest req) {
        log.warn("Not implemented: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED)
                .body(ErrorResponse.of("FIN_NOT_IMPLEMENTED",
                        ex.getMessage() != null ? ex.getMessage() : "This endpoint is not yet implemented",
                        req.getRequestURI()));
    }

    /**
     * Catches {@code IllegalStateException} from service/controller code that
     * encounters an unexpected internal state (e.g., missing security context
     * when it should always be present).  Maps to HTTP 500.
     */
    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ErrorResponse> handleIllegalState(
            IllegalStateException ex, HttpServletRequest req) {
        log.error("Unexpected internal state error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("FIN_INTERNAL_ERROR",
                        "An unexpected internal error occurred. Please contact support.",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Spring MVC validation
    // =========================================================================

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> fieldErrors = new HashMap<String, String>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }
        log.debug("Validation failed: {}", fieldErrors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Request validation failed",
                        fieldErrors, req.getRequestURI()));
    }

    // =========================================================================
    // Multipart / file upload errors
    // =========================================================================

    @ExceptionHandler(MultipartException.class)
    public ResponseEntity<ErrorResponse> handleMultipart(
            MultipartException ex, HttpServletRequest req) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR",
                        "Expected a multipart/form-data request with a 'file' part. " +
                        "Ensure Content-Type is not overridden by the client.",
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
    // Spring MVC deserialization / type-conversion errors
    // =========================================================================

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleMessageNotReadable(
            HttpMessageNotReadableException ex, HttpServletRequest req) {
        String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", "Malformed request body: " + detail, req.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        String detail = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("VALIDATION_ERROR", detail, req.getRequestURI()));
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
        String queryString = req.getQueryString();
        Map<String, String[]> paramMap = req.getParameterMap();
        String xEntityId = req.getHeader("X-Entity-Id");
        String xOrgId = req.getHeader("X-Organization-Id");

        log.warn("Missing required request parameter '{}' (type {}) on {} {}",
                ex.getParameterName(), ex.getParameterType(),
                req.getMethod(), req.getRequestURI());
        log.warn("Query string: {}", queryString != null ? queryString : "(none)");
        log.warn("Parameter names received: {}",
                paramMap.isEmpty()
                        ? "(none — no query params or form params sent)"
                        : String.join(", ", paramMap.keySet()));
        log.warn("X-Entity-Id header: {}", xEntityId != null ? xEntityId : "(not sent)");
        log.warn("X-Organization-Id header: {}", xOrgId != null ? xOrgId : "(not sent)");

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ErrorResponse.of("MISSING_PARAM",
                        "Required request parameter '" + ex.getParameterName()
                                + "' is not present",
                        req.getRequestURI()));
    }

    // =========================================================================
    // Catch-all
    // =========================================================================

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(
            Exception ex, HttpServletRequest req) throws Exception {
        // Diagnostic logging for MissingServletRequestParameterException that
        // might slip through to the catch-all
        if (ex instanceof MissingServletRequestParameterException mspe) {
            String queryString = req.getQueryString();
            Map<String, String[]> paramMap = req.getParameterMap();
            String xEntityId = req.getHeader("X-Entity-Id");
            String xOrgId = req.getHeader("X-Organization-Id");

            log.warn("(catch-all) Missing required request parameter '{}' (type {}) on {} {}",
                    mspe.getParameterName(), mspe.getParameterType(),
                    req.getMethod(), req.getRequestURI());
            log.warn("(catch-all) Query string: {}", queryString != null ? queryString : "(none)");
            log.warn("(catch-all) Parameter names received: {}",
                    paramMap.isEmpty()
                            ? "(none — no query params or form params sent)"
                            : String.join(", ", paramMap.keySet()));
            log.warn("(catch-all) X-Entity-Id header: {}", xEntityId != null ? xEntityId : "(not sent)");
            log.warn("(catch-all) X-Organization-Id header: {}", xOrgId != null ? xOrgId : "(not sent)");
        }

        log.error("Unexpected error on {}: {}", req.getRequestURI(), ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponse.of("INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again.",
                        req.getRequestURI()));
    }
}