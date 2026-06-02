package com.af.novadesk.api.payroll.constants;

import com.af.novadesk.api.payroll.entity.Payslip;

/**
 * Domain events produced by the {@link Payslip} aggregate (LLR-PAY-02.5).
 */
public enum PayslipEventType {

    /** Fired when a payslip PDF is generated → triggers employee notification. */
    PAYSLIP_GENERATED
}
