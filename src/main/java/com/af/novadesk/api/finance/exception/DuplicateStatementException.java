package com.af.novadesk.api.finance.exception;

/**
 * Thrown when an uploaded statement's bank account + period matches an existing
 * non-superseded statement. The caller should present a "Replace or Cancel" choice.
 */
public class DuplicateStatementException extends FinanceBaseException {

    private static final String ERROR_CODE = "FIN_BNK_002";

    public DuplicateStatementException(String message) {
        super(ERROR_CODE, message);
    }
}
