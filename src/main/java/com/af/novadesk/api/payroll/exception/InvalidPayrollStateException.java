package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;

import java.util.UUID;

/**
 * Thrown when an invalid payroll batch state transition is attempted.
 */
public class InvalidPayrollStateException extends PayrollBaseException {

    public InvalidPayrollStateException(UUID batchId, PayrollBatchStatus current, PayrollBatchStatus target) {
        super("PAY_PB_003",
                String.format("Cannot transition payroll batch %s from %s to %s",
                        batchId, current, target));
    }

    public InvalidPayrollStateException(String message) {
        super("PAY_PB_003", message);
    }
}
