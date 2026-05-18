package com.af.novadesk.api.finance.funding.controller;

import com.af.novadesk.api.finance.funding.exception.BadRequestException;
import com.af.novadesk.api.finance.funding.exception.MissingExchangeRateException;
import com.af.novadesk.api.finance.funding.exception.NotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Centralised exception handler for the {@code finance.funding} controller slice.
 *
 * <p>Uses RFC 9457 {@link ProblemDetail} for structured, client-parseable error
 * responses.  Each problem carries a {@code timestamp} extension property so
 * clients and log-aggregators can correlate errors by time.</p>
 */
@RestControllerAdvice(basePackageClasses = CapitalInjectionController.class)
public class FundingExceptionHandler {

    private static final URI BAD_REQUEST_TYPE     = URI.create("urn:af:novadesk:error:bad-request");
    private static final URI NOT_FOUND_TYPE       = URI.create("urn:af:novadesk:error:not-found");
    private static final URI MISSING_RATE_TYPE    = URI.create("urn:af:novadesk:error:missing-exchange-rate");
    private static final URI VALIDATION_TYPE      = URI.create("urn:af:novadesk:error:validation");

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

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    private ProblemDetail problem(HttpStatus status, URI type, String title, String detail) {
        ProblemDetail pd = ProblemDetail.forStatusAndDetail(status, detail);
        pd.setType(type);
        pd.setTitle(title);
        pd.setProperty("timestamp", Instant.now().toString());
        return pd;
    }
}

