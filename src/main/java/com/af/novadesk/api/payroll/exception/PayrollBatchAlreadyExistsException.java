package com.af.novadesk.api.payroll.exception;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Thrown when attempting to create a PayrollBatch that already exists
 * for the same entity + pay period.
 */
public class PayrollBatchAlreadyExistsException extends PayrollBaseException {

    public PayrollBatchAlreadyExistsException(UUID legalEntityId, LocalDate periodStart, LocalDate periodEnd) {
        super("PAY_PB_002",
                String.format("Payroll batch already exists for entity %s, period %s to %s",
                        legalEntityId, periodStart, periodEnd));
    }
}
