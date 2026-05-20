package com.af.novadesk.api.identity.constants;

/**
 * This file is deprecated - use com.af.novadesk.api.common.constants.OutboxEventStatus instead.
 * Kept for backward compatibility.
 */
public enum OutboxEventStatus {
    PENDING(com.af.novadesk.api.common.constants.OutboxEventStatus.PENDING),
    PUBLISHED(com.af.novadesk.api.common.constants.OutboxEventStatus.PUBLISHED),
    FAILED(com.af.novadesk.api.common.constants.OutboxEventStatus.FAILED),
    DEAD(com.af.novadesk.api.common.constants.OutboxEventStatus.DEAD);

    private final com.af.novadesk.api.common.constants.OutboxEventStatus commonStatus;

    OutboxEventStatus(com.af.novadesk.api.common.constants.OutboxEventStatus commonStatus) {
        this.commonStatus = commonStatus;
    }

    public com.af.novadesk.api.common.constants.OutboxEventStatus toCommon() {
        return commonStatus;
    }
}
