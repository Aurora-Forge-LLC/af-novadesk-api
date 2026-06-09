package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when an {@link com.af.novadesk.api.finance.entity.ExpenseTransaction}
 * with the given ID does not exist or is not accessible within the caller's
 * organization scope. Maps to HTTP 404 Not Found.
 */
public class ExpenseTransactionNotFoundException extends FinanceBaseException {

    public ExpenseTransactionNotFoundException(UUID transactionId) {
        super("FIN_EXPENSE_001",
                String.format("Expense transaction not found: %s", transactionId));
    }

    public ExpenseTransactionNotFoundException(String message) {
        super("FIN_EXPENSE_001", message);
    }
}
