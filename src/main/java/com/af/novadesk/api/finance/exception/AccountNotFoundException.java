package com.af.novadesk.api.finance.exception;

import java.util.UUID;

/**
 * Thrown when a requested Account resource cannot be found in the database.
 * Maps to HTTP 404 Not Found.
 */
public class AccountNotFoundException extends FinanceBaseException {

    public AccountNotFoundException(UUID accountId) {
        super("FIN_ACCOUNT_001",
                String.format("Account not found with id: %s", accountId));
    }

    public AccountNotFoundException(String message) {
        super("FIN_ACCOUNT_001", message);
    }
}
