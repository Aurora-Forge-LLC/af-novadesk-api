package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.LegalEntity;

import java.util.UUID;

/**
 * Outbox publisher contract for the {@link LegalEntity} aggregate.
 * Each method persists an outbox event row inside the caller's active transaction.
 */
public interface LegalEntityOutboxService {

    void publishEntityCreated(LegalEntity entity, UUID triggeredBy, UUID orgId);

    void publishEntityApproved(LegalEntity entity, UUID triggeredBy, UUID orgId);

    void publishEntityRejected(LegalEntity entity, String reason, UUID triggeredBy, UUID orgId);

    void publishStatusChanged(LegalEntity entity, Status previous, Status next,
                              UUID triggeredBy, UUID orgId);
}
