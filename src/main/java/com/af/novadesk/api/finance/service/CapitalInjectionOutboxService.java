package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.entity.CapitalInjection;
import com.af.novadesk.api.finance.entity.LegalEntity;

import java.util.UUID;

/**
 * Outbox publisher contract for the {@link CapitalInjection} aggregate (LLR-FIN-02).
 * Each method persists an outbox event row inside the caller's active transaction.
 */
public interface CapitalInjectionOutboxService {

    void publishCapitalInjectionCreated(CapitalInjection saved,
                                        UUID journalId,
                                        LegalEntity targetEntity,
                                        LegalEntity sourceEntity,
                                        String callerIdentity);
}
