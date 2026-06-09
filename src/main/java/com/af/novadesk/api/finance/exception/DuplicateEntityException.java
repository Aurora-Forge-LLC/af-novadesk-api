package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when an attempt is made to create a {@code LegalEntity} with a name
 * or code that already exists within the organization. (LLR-FIN-01.1 uniqueness).
 */
public class DuplicateEntityException extends FinanceBaseException {
    public DuplicateEntityException(String field, String value) {
        super("FIN_ENTITY_002",
                String.format("Legal entity with %s '%s' already exists", field, value));
    }

}
