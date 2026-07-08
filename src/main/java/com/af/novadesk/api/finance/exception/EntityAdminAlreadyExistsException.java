package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when attempting to grant or promote a user to ENTITY_ADMIN on an
 * entity that already has an active ENTITY_ADMIN. Only one ENTITY_ADMIN is
 * allowed per legal entity.
 */
public class EntityAdminAlreadyExistsException extends FinanceBaseException {
    public EntityAdminAlreadyExistsException(UUID entityId) {
        super("FIN_ACCESS_004",
                String.format("Entity %s already has an ENTITY_ADMIN. Only one ENTITY_ADMIN is allowed per entity.", entityId));
    }
}
