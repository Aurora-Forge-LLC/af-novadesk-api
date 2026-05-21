package com.af.novadesk.api.finance.exception;

/**
 * Thrown when double-entry validation fails — total debits do not equal total
 * credits in either local currency or USD. Maps to HTTP 400 Bad Request.
 */
public class UnbalancedLedgerException extends FinanceBaseException {

    public UnbalancedLedgerException(String message) {
        super("FIN_LEDGER_001", message);
    }
}
