package com.af.novadesk.api.finance.exception;

/**
 * Thrown when a bank transaction is not found by the given UUID.
 */
public class BankTransactionNotFoundException extends FinanceBaseException {

    private static final String ERROR_CODE = "FIN_BNK_005";

    public BankTransactionNotFoundException(java.util.UUID transactionId) {
        super(ERROR_CODE, "Bank transaction not found: " + transactionId);
    }
}
