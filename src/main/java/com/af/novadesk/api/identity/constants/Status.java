package com.af.novadesk.api.identity.constants;

/**
 * This file is deprecated - use com.af.novadesk.api.common.constants.Status instead.
 * Kept for backward compatibility.
 */
public enum Status {
    ACTIVE(com.af.novadesk.api.common.constants.Status.ACTIVE),
    INACTIVE(com.af.novadesk.api.common.constants.Status.INACTIVE),
    SUSPENDED(com.af.novadesk.api.common.constants.Status.SUSPENDED),
    DELETED(com.af.novadesk.api.common.constants.Status.DELETED);

    private final com.af.novadesk.api.common.constants.Status commonStatus;

    Status(com.af.novadesk.api.common.constants.Status commonStatus) {
        this.commonStatus = commonStatus;
    }

    public com.af.novadesk.api.common.constants.Status toCommon() {
        return commonStatus;
    }
}
