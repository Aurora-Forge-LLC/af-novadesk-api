package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.ExpenseTransactionStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * Full ledger view for a single expense transaction.
 *
 * <p>Contains one journal for a POSTED expense and two journals for a VOID expense
 * (ORIGINAL + VOID_REVERSAL).  Each journal holds exactly two balanced entries.</p>
 */
@Schema(description = "All ledger journals posted for an expense transaction")
public record ExpenseLedgerResponse(

        @Schema(description = "Expense transaction UUID")
        UUID transactionId,

        @Schema(description = "Current accounting state of the transaction")
        ExpenseTransactionStatus transactionStatus,

        @Schema(description = "Journals in chronological order — ORIGINAL first, VOID_REVERSAL second (if voided)")
        List<ExpenseLedgerJournalDto> journals
) {
}
