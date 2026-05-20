package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when an {@code EntityUserAccess} record is not found for a
 * given access ID within the entity scope.
 */
public class UserAccessNotFoundException extends FinanceBaseException {
    public UserAccessNotFoundException(UUID accessId) {
        super("FIN_ACCESS_001",
                String.format("User access record not found: %s", accessId));
    }
}