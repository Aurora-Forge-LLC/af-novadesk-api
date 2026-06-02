package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when a Payslip with a given ID is not found.
 */
public class PayslipNotFoundException extends PayrollBaseException {

    public PayslipNotFoundException(UUID payslipId) {
        super("PAY_PS_001", String.format("Payslip not found: %s", payslipId));
    }

    public PayslipNotFoundException(UUID employeeId, UUID batchId) {
        super("PAY_PS_001",
                String.format("Payslip not found for employee %s in batch %s",
                        employeeId, batchId));
    }
}
