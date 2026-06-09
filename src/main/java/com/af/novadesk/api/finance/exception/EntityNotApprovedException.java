package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when an operation is attempted on a legal entity that has not been
 * approved yet, or is not in an active state. Maps to HTTP 400 Bad Request.
 */
public class EntityNotApprovedException extends FinanceBaseException {

    public EntityNotApprovedException(String entityCode, String reason) {
        super("FIN_ENTITY_004",
                String.format("Entity '%s' is %s", entityCode, reason));
    }
}
