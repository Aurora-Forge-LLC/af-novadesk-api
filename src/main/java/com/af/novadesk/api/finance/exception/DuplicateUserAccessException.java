package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a caller attempts to grant access to a user who already
 * has an active access record for the same entity. (LLR-FIN-01.3 uniqueness).
 */
public class DuplicateUserAccessException extends FinanceBaseException {
    public DuplicateUserAccessException(UUID authUserId, UUID entityId) {
        super("FIN_ACCESS_002",
                String.format("User %s already has access to entity %s", authUserId, entityId));
    }
}
