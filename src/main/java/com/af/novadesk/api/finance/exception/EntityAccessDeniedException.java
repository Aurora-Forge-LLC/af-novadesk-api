package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when the caller does not have a permission to perform the
 * requested operation on the given entity.
 */
public class EntityAccessDeniedException extends FinanceBaseException {
    public EntityAccessDeniedException(UUID authUserId, UUID entityId) {
        super("FIN_ACCESS_003",
                String.format("User %s does not have access to entity %s", authUserId, entityId));
    }
}