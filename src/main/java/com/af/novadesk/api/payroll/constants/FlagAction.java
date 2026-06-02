package com.af.novadesk.api.payroll.constants;

/**
 * Executive actions available for flagged employees in the review queue (LLR-PAY-02.3).
 */
public enum FlagAction {
    PENDING_REVIEW,     // Awaiting executive action
    WAIVED,             // Executive waived → full salary processed
    PRORATED            // Executive prorated → adjusted salary calculated
}
