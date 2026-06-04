package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.entity.EntityUserAccess;

import java.util.UUID;

/**
 * Outbox publisher contract for the {@link EntityUserAccess} aggregate.
 * Each method persists an outbox event row inside the caller's active transaction.
 */
public interface EntityUserAccessOutboxService {

    void publishAccessGranted(EntityUserAccess access, UUID triggeredBy, UUID orgId);

    void publishAccessRevoked(EntityUserAccess access, UUID triggeredBy, UUID orgId);

    void publishRoleChanged(EntityUserAccess access, String previousRole,
                            String newRole, UUID triggeredBy, UUID orgId);
}
