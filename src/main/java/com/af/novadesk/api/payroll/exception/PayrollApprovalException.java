package com.af.novadesk.api.payroll.exception;

import java.util.List;
import java.util.UUID;

/**
 * Thrown when payroll pre-checks fail before batch initiation
 * (e.g., pending leave requests, missing bank accounts, incomplete tax config).
 */
public class PayrollApprovalException extends PayrollBaseException {

    private final List<String> preCheckFailures;

    public PayrollApprovalException(UUID legalEntityId, List<String> failures) {
        super("PAY_PB_005",
                String.format("Payroll pre-checks failed for entity %s: %d issues found",
                        legalEntityId, failures.size()));
        this.preCheckFailures = failures;
    }

    public PayrollApprovalException(String message) {
        super("PAY_PB_005", message);
        this.preCheckFailures = List.of(message);
    }

    public List<String> getPreCheckFailures() {
        return preCheckFailures;
    }
}
