package com.af.novadesk.api.common.constants;

/**
 * Lifecycle status of a {@link com.af.novadesk.api.common.entity.CmEmployee}.
 */
public enum EmployeeStatus {
    /**
     * Employee created in novadesk but AuthHub account has not yet been activated
     * (password not set). Employee cannot log in.
     */
    PENDING_SETUP,
    /** Actively employed — password set, account active. */
    ACTIVE,
    /** Employment suspended or on long-term leave. */
    INACTIVE,
    /**
     * Employee has been offboarded — AuthHub account deactivated,
     * tokens revoked, roles removed. Cannot access the system.
     * Data is preserved for audit/reporting.
     */
    OFFBOARDED
}
