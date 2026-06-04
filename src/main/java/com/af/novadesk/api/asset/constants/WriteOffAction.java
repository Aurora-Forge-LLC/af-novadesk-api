package com.af.novadesk.api.asset.constants;

public enum WriteOffAction {
    /** Asset written off at depreciated value — offboarding unblocked. */
    WRITE_OFF,
    /** Depreciated value deducted from employee's final pay. */
    DEDUCT_FROM_PAY,
    /** Invoice generated for employee to reimburse the depreciated value. */
    REQUIRE_REIMBURSEMENT
}
