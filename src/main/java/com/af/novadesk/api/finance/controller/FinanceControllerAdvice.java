package com.af.novadesk.api.finance.controller;

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
@Deprecated
public final class FinanceControllerAdvice {
    private FinanceControllerAdvice() {
        // utility class — no instances
    }
}
