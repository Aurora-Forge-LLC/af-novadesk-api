package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a requested ExchangeRate resource cannot be found in the database.
 * Maps to HTTP 404 Not Found.
 */
public class ExchangeRateNotFoundException extends FinanceBaseException {

    public ExchangeRateNotFoundException(UUID id) {
        super("FIN_RATE_001",
                String.format("Exchange rate not found with id: %s", id));
    }

    public ExchangeRateNotFoundException(String message) {
        super("FIN_RATE_001", message);
    }
}
