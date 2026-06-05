package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Base exception for the payroll module. Extends {@link FinanceBaseException}
 * from the common module. All payroll exceptions carry structured
 * {@code PAY_*} error codes for programmatic handling by API consumers.
 */
public abstract class PayrollBaseException extends FinanceBaseException {

    protected PayrollBaseException(String errorCode, String message) {
        super(errorCode, message);
    }

    protected PayrollBaseException(String errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
