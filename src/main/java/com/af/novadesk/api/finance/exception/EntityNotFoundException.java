package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when a {@code LegalEntity} with a given ID does not exist or
 * is not accessible within the caller's organization scope.
 */
public class EntityNotFoundException extends FinanceBaseException {
    public EntityNotFoundException(UUID entityId) {
        super("FIN_ENTITY_001",
                String.format("Legal entity not found: %s", entityId));
    }
}