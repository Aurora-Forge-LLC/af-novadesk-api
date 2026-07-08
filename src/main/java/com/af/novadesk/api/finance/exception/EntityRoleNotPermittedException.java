package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when an ORG_ADMIN attempts to grant, update, or revoke an entity-tier
 * role other than ENTITY_ADMIN. ORG_ADMIN may only bootstrap an entity's
 * ENTITY_ADMIN; every other entity-tier role must be managed by that entity's
 * own ENTITY_ADMIN.
 */
public class EntityRoleNotPermittedException extends FinanceBaseException {
    public EntityRoleNotPermittedException(UUID entityId, String entityRole) {
        super("FIN_ACCESS_005",
                String.format(
                        "ORG_ADMIN may only grant or manage the ENTITY_ADMIN role on entity %s. "
                        + "The '%s' role must be managed by that entity's own ENTITY_ADMIN.",
                        entityId, entityRole));
    }
}
