package com.af.novadesk.api.payroll.exception;

import java.math.BigDecimal;

/**
 * Thrown when an employee attempts to take more leave than allowed
 * by the earned leave borrowing rules for a policy.
 */
public class LeaveBorrowLimitExceededException extends PayrollBaseException {

    public LeaveBorrowLimitExceededException(String policyName, BigDecimal earned,
                                              BigDecimal borrowLimit, BigDecimal requested) {
        super("PAY_LB_003",
                String.format(
                        "Borrow limit exceeded for '%s'. Earned: %.1f, Borrow limit: %.1f, Requested: %.1f. "
                                + "Please apply for unpaid leave instead.",
                        policyName, earned, borrowLimit, requested));
    }
}
