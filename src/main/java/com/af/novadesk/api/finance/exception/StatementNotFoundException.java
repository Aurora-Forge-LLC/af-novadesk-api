package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a bank statement with the given ID does not exist (LLR-BNK-01).
 */
public class StatementNotFoundException extends FinanceBaseException {
    public StatementNotFoundException(UUID statementId) {
        super("FIN_BNK_001",
                String.format("Bank statement not found: %s", statementId));
    }
}
