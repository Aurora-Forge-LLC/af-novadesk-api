package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when a bank statement file cannot be parsed (malformed CSV/Excel) (LLR-BNK-01).
 */
public class StatementParseException extends FinanceBaseException {
    public StatementParseException(String message) {
        super("FIN_BNK_003", "Failed to parse bank statement: " + message);
    }

    public StatementParseException(String message, Throwable cause) {
        super("FIN_BNK_003", "Failed to parse bank statement: " + message, cause);
    }
}
