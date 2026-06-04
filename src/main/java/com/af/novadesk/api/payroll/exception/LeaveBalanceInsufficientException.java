package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.payroll.constants.LeaveType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thrown when leave balance is insufficient for the requested leave days.
 * The caller can use the available paid/sick days to auto-convert the
 * excess to Unpaid leave per LLR-PAY-01.2.
 */
public class LeaveBalanceInsufficientException extends PayrollBaseException {

    private final BigDecimal availablePaidDays;
    private final BigDecimal availableSickDays;
    private final BigDecimal requestedDays;

    public LeaveBalanceInsufficientException(UUID employeeId, LeaveType leaveType,
                                              BigDecimal available, BigDecimal requested) {
        super("PAY_LB_002",
                String.format("Insufficient %s leave balance for employee %s: available %.1f, requested %.1f",
                        leaveType, employeeId, available, requested));
        this.availablePaidDays = leaveType == LeaveType.PAID ? available : BigDecimal.ZERO;
        this.availableSickDays = leaveType == LeaveType.SICK ? available : BigDecimal.ZERO;
        this.requestedDays = requested;
    }

    public BigDecimal getAvailablePaidDays() { return availablePaidDays; }
    public BigDecimal getAvailableSickDays() { return availableSickDays; }
    public BigDecimal getRequestedDays() { return requestedDays; }
}
