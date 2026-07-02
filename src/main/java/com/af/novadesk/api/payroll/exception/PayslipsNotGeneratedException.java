package com.af.novadesk.api.payroll.exception;

/**
 * Thrown when attempting to approve or reject a payroll batch
 * before payslips have been generated.
 *
 * <p>Error Code: {@code PAY_006}</p>
 */
public class PayslipsNotGeneratedException extends PayrollBaseException {

    private static final String ERROR_CODE = "PAY_006";

    public PayslipsNotGeneratedException(String message) {
        super(ERROR_CODE, message);
    }

    public PayslipsNotGeneratedException(String message, Throwable cause) {
        super(ERROR_CODE, message, cause);
    }

    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
}
