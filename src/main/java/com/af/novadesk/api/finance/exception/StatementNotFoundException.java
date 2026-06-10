package com.af.novadesk.api.finance.exception;

/**
 * Thrown when a bank statement is not found by the given UUID.
 */
public class StatementNotFoundException extends FinanceBaseException {

    private static final String ERROR_CODE = "FIN_BNK_001";

    public StatementNotFoundException(java.util.UUID statementId) {
        super(ERROR_CODE, "Bank statement not found: " + statementId);
    }
}
