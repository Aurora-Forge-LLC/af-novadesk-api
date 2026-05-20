package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when a state-transition is attempted on a {@code LegalEntity}
 * that is in an incompatible {@code ApprovalStatus}.
 * E.g. trying to approve an already-approved entity, or approve a REJECTED one.
 */
public class InvalidEntityStateException extends FinanceBaseException {
    public InvalidEntityStateException(UUID entityId, String currentState, String attemptedOperation) {
        super("FIN_ENTITY_003",
                String.format("Cannot '%s' legal entity %s — current state is '%s'",
                        attemptedOperation, entityId, currentState));
    }
}