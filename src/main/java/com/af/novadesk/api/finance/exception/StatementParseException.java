package com.af.novadesk.api.finance.exception;

/**
 * Thrown when parsing of a bank statement file fails (malformed CSV, Excel, etc.)
 */
public class StatementParseException extends FinanceBaseException {

    private static final String ERROR_CODE = "FIN_BNK_003";

    public StatementParseException(String message) {
        super(ERROR_CODE, message);
    }

    public StatementParseException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }
}
