package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a bank statement upload conflicts with an existing statement
 * for the same bank account and period (LLR-BNK-01).
 */
public class DuplicateStatementException extends FinanceBaseException {
    public DuplicateStatementException(UUID bankAccountId, java.time.LocalDate periodStart, java.time.LocalDate periodEnd) {
        super("FIN_BNK_002",
                String.format("A statement already exists for bank account %s for period %s to %s",
                        bankAccountId, periodStart, periodEnd));
    }
}
