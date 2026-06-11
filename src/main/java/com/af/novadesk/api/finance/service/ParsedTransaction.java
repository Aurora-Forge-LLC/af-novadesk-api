package com.af.novadesk.api.finance.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Internal record representing a single parsed transaction from a bank statement.
 *
 * <p>This is not a DTO — it is an internal representation used by parsers
 * to pass extracted data to the service layer for persistence.</p>
 *
 * @param transactionDate the date of the transaction
 * @param description     the transaction description/narration
 * @param debit           debit amount (money out); null if credit-only row
 * @param credit          credit amount (money in); null if debit-only row
 * @param balance         running balance after this transaction; may be null
 */
public record ParsedTransaction(
        LocalDate transactionDate,
        String description,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal balance
) {
    /**
     * Returns the signed amount: positive for credit, negative for debit.
     */
    public BigDecimal signedAmount() {
        if (credit != null) return credit;
        if (debit != null) return debit.negate();
        return BigDecimal.ZERO;
    }
}
