package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when the entity's ENTITY_ADMIN attempts to revoke their own access
 * grant or change their own role away from ENTITY_ADMIN on that entity.
 */
public class SelfEntityAdminActionException extends FinanceBaseException {
    public SelfEntityAdminActionException(UUID entityId) {
        super("FIN_ACCESS_006",
                String.format(
                        "The ENTITY_ADMIN cannot remove themself or change their own role on entity %s.",
                        entityId));
    }
}
