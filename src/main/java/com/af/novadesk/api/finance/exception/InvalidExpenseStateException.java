package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a state-transition is attempted on an
 * {@link com.af.novadesk.api.finance.entity.ExpenseTransaction} that is in an
 * incompatible status. For example: voiding a transaction that is already VOID,
 * or voiding a DRAFT that has not been posted yet.
 * Maps to HTTP 422 Unprocessable Entity.
 */
public class InvalidExpenseStateException extends FinanceBaseException {

    public InvalidExpenseStateException(UUID transactionId, String currentState, String attemptedOperation) {
        super("FIN_EXPENSE_002",
                String.format("Cannot '%s' expense transaction %s — current state is '%s'",
                        attemptedOperation, transactionId, currentState));
    }

    public InvalidExpenseStateException(String message) {
        super("FIN_EXPENSE_002", message);
    }
}
