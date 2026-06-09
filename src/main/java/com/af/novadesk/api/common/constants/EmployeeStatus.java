package com.af.novadesk.api.common.constants;

/**
 * Lifecycle status of a {@link com.af.novadesk.api.common.entity.CmEmployee}.
 */
public enum EmployeeStatus {
    /** Actively employed. */
    ACTIVE,
    /** Employment suspended or on long-term leave. */
    INACTIVE,
    /** Offboarding in progress — asset return gate applies. */
    OFFBOARDING
}
