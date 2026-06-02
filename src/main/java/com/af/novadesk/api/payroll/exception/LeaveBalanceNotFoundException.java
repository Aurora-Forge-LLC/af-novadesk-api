package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.payroll.constants.LeaveType;

import java.util.UUID;

/**
 * Thrown when a LeaveBalance record is not found for a given
 * employee + leave type + fiscal year combination.
 */
public class LeaveBalanceNotFoundException extends PayrollBaseException {

    public LeaveBalanceNotFoundException(UUID employeeId, LeaveType leaveType) {
        super("PAY_LB_001",
                String.format("Leave balance not found for employee %s and type %s",
                        employeeId, leaveType));
    }

    public LeaveBalanceNotFoundException(UUID employeeId, LeaveType leaveType, UUID fiscalYearSettingId) {
        super("PAY_LB_001",
                String.format("Leave balance not found for employee %s, type %s, fiscal year %s",
                        employeeId, leaveType, fiscalYearSettingId));
    }
}
