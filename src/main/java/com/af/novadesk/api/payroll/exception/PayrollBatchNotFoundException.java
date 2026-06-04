package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when a PayrollBatch with a given ID is not found.
 */
public class PayrollBatchNotFoundException extends PayrollBaseException {

    public PayrollBatchNotFoundException(UUID batchId) {
        super("PAY_PB_001", String.format("Payroll batch not found: %s", batchId));
    }
}
