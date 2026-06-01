package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.finance.exception.AttachmentNotFoundException;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.DuplicateVendorException;
import com.af.novadesk.api.finance.exception.ExpenseTransactionNotFoundException;
import com.af.novadesk.api.finance.exception.InvalidExpenseStateException;
import com.af.novadesk.api.finance.exception.JwtClaimMissingException;
import com.af.novadesk.api.finance.exception.MissingExchangeRateException;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.exception.VendorNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * <strong>Deprecated</strong> — all exception handlers have been consolidated into
 * {@link com.af.novadesk.api.finance.exception.FinanceExceptionHandler} which
 * scans the broader {@code com.af.novadesk.api} package and provides a
 * consistent {@code ErrorResponse} envelope with structured {@code errorCode}
 * fields for programmatic consumption.
 *
 * <p>This class is retained as an empty no-op to avoid compilation errors in
 * any existing code that references it.  It will be removed in a future
 * cleanup pass.</p>
 *
 * @deprecated Use {@link com.af.novadesk.api.finance.exception.FinanceExceptionHandler} instead.
 */
@RestControllerAdvice(basePackages = "com.af.novadesk.api.finance.controller")
public class FinanceControllerAdvice {

    private static final Logger log = LoggerFactory.getLogger(FinanceControllerAdvice.class);

    private static final URI BAD_REQUEST_TYPE     = URI.create("urn:af:novadesk:error:bad-request");
    private static final URI NOT_FOUND_TYPE       = URI.create("urn:af:novadesk:error:not-found");
    private static final URI DUPLICATE_TYPE       = URI.create("urn:af:novadesk:error:duplicate");
    private static final URI UNPROCESSABLE_TYPE   = URI.create("urn:af:novadesk:error:unprocessable");
    private static final URI MISSING_RATE_TYPE    = URI.create("urn:af:novadesk:error:missing-exchange-rate");
    private static final URI VALIDATION_TYPE      = URI.create("urn:af:novadesk:error:validation");
    private static final URI INTERNAL_TYPE        = URI.create("urn:af:novadesk:error:internal");

    // =========================================================================
    // Vendor exceptions
    // =========================================================================

    @ExceptionHandler(VendorNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleVendorNotFound(VendorNotFoundException ex) {
        log.warn("Vendor not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Vendor Not Found", ex.getMessage());
    }

    @ExceptionHandler(DuplicateVendorException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleDuplicateVendor(DuplicateVendorException ex) {
        log.warn("Duplicate vendor: {}", ex.getMessage());
        return problem(HttpStatus.CONFLICT, DUPLICATE_TYPE, "Duplicate Vendor", ex.getMessage());
    }

    // =========================================================================
    // Expense transaction exceptions
    // =========================================================================

    @ExceptionHandler(ExpenseTransactionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleExpenseTransactionNotFound(ExpenseTransactionNotFoundException ex) {
        log.warn("Expense transaction not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Expense Transaction Not Found", ex.getMessage());
    }

    @ExceptionHandler(InvalidExpenseStateException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail handleInvalidExpenseState(InvalidExpenseStateException ex) {
        log.warn("Invalid expense state transition: {}", ex.getMessage());
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, UNPROCESSABLE_TYPE,
                "Invalid Expense State", ex.getMessage());
    }

    @ExceptionHandler(AttachmentNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleAttachmentNotFound(AttachmentNotFoundException ex) {
        log.warn("Attachment not found: {}", ex.getMessage());
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Attachment Not Found", ex.getMessage());
    }

    // =========================================================================
    // Spring MVC deserialization / type-conversion errors
    // =========================================================================

    /**
     * Handles Jackson deserialization failures — most commonly an unknown enum value
     * (e.g. {@code "vendor_type": "INVALID_TYPE"}).  Returns 400 instead of letting
     * the catch-all promote it to 500.
     */
    /**
     * Handles multipart requests where a required file part is absent
     * (e.g. POST /attachments called without a {@code file} form field).
     * Returns 400 instead of letting the catch-all promote it to 500.
     */
    @ExceptionHandler(MissingServletRequestPartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMissingPart(MissingServletRequestPartException ex) {
        String detail = String.format("Required request part '%s' is missing", ex.getRequestPartName());
        log.debug("Missing multipart part: {}", detail);
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE, "Missing Request Part", detail);
    }

    /**
     * Handles requests sent to a multipart endpoint with the wrong Content-Type
     * (e.g. POST /attachments with Content-Type: application/json instead of multipart/form-data).
     * Returns 400 instead of letting the catch-all promote it to 500.
     */
    @ExceptionHandler(MultipartException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMultipartError(MultipartException ex) {
        log.debug("Multipart error: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE,
                "Invalid Request",
                "Expected a multipart/form-data request with a 'file' part");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMessageNotReadable(HttpMessageNotReadableException ex) {
        log.debug("Unreadable request body: {}", ex.getMessage());
        String detail = ex.getCause() != null ? ex.getCause().getMessage() : ex.getMessage();
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE, "Malformed Request Body", detail);
    }

    /**
     * Handles path-variable type-conversion failures — e.g. {@code /vendors/not-a-uuid}
     * where the path variable is typed as {@code UUID}.  Returns 400.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = String.format("Invalid value '%s' for parameter '%s'", ex.getValue(), ex.getName());
        log.debug("Type mismatch: {}", detail);
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE, "Invalid Parameter", detail);
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ProblemDetail handleNotFound(NotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, NOT_FOUND_TYPE, "Not Found", ex.getMessage());
    }

    @ExceptionHandler(MissingExchangeRateException.class)
    @ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
    public ProblemDetail handleMissingRate(MissingExchangeRateException ex) {
        return problem(HttpStatus.UNPROCESSABLE_ENTITY, MISSING_RATE_TYPE,
                "Missing Exchange Rate", ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        ProblemDetail pd = problem(HttpStatus.BAD_REQUEST, VALIDATION_TYPE,
                "Validation Failed", details);
        return pd;
    }

    @ExceptionHandler(JwtClaimMissingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleJwtClaimMissing(JwtClaimMissingException ex) {
        log.warn("JWT claim missing: {}", ex.getMessage());
        return problem(HttpStatus.BAD_REQUEST, BAD_REQUEST_TYPE, "Bad Request", ex.getMessage());
    }

    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ProblemDetail handleIllegalState(IllegalStateException ex) {
        log.error("Unexpected internal state error", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, INTERNAL_TYPE,
                "Internal Server Error",
                "An unexpected internal error occurred. Please contact support.");
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(type);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
@Deprecated
public final class FinanceControllerAdvice {
    private FinanceControllerAdvice() {
        // utility class — no instances
    }
}
