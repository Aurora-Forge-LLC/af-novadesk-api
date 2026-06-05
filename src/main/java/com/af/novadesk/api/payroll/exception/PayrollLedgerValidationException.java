package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when payroll journal entries are unbalanced
 * (SUM(debits) != SUM(credits)).
 */
public class PayrollLedgerValidationException extends PayrollBaseException {

    public PayrollLedgerValidationException(UUID journalId) {
        super("PAY_PLE_001",
                String.format("Payroll journal entries unbalanced for journal %s: total debits must equal total credits", journalId));
    }

    public PayrollLedgerValidationException(String message) {
        super("PAY_PLE_001", message);
    }
}
