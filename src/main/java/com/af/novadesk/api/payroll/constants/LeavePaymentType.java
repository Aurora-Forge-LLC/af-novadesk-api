package com.af.novadesk.api.payroll.constants;

/**
 * Payment type for a leave policy — whether the leave is paid or unpaid.
 * Distinct from the legacy {@link LeaveType} enum which is being deprecated
 * in favor of the configurable {@code LeavePolicy} rule engine.
 */
public enum LeavePaymentType {
    PAID,
    UNPAID
}
