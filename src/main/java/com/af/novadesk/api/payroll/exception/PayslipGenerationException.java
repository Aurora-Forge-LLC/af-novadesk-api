package com.af.novadesk.api.payroll.exception;

import java.util.UUID;

/**
 * Thrown when payslip PDF generation fails (e.g., template error, MinIO upload failure).
 */
public class PayslipGenerationException extends PayrollBaseException {

    public PayslipGenerationException(UUID payslipId, String message) {
        super("PAY_PS_002",
                String.format("Failed to generate payslip PDF for %s: %s", payslipId, message));
    }

    public PayslipGenerationException(UUID payslipId, String message, Throwable cause) {
        super("PAY_PS_002",
                String.format("Failed to generate payslip PDF for %s: %s", payslipId, message),
                cause);
    }
}
