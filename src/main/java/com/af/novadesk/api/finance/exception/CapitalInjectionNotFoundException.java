package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a requested CapitalInjection resource cannot be found.
 * Maps to HTTP 404 Not Found.
 */
public class CapitalInjectionNotFoundException extends FinanceBaseException {

    public CapitalInjectionNotFoundException(UUID id) {
        super("FIN_CAPITAL_001",
                String.format("Capital injection not found with id: %s", id));
    }

    public CapitalInjectionNotFoundException(String message) {
        super("FIN_CAPITAL_001", message);
    }
}
