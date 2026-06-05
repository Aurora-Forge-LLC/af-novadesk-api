package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when a PayrollFlaggedEmployee record is not found.
 */
public class PayrollFlaggedEmployeeNotFoundException extends PayrollBaseException {

    public PayrollFlaggedEmployeeNotFoundException(UUID flaggedId) {
        super("PAY_PFE_001", String.format("Flagged employee record not found: %s", flaggedId));
    }

    public PayrollFlaggedEmployeeNotFoundException(UUID batchId, UUID employeeId) {
        super("PAY_PFE_001",
                String.format("Flagged employee record not found for employee %s in batch %s",
                        employeeId, batchId));
    }
}
