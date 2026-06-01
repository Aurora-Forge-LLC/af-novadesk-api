package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;
import java.util.UUID;

/**
 * One journal batch (two ledger entries) belonging to an expense transaction.
 *
 * <p>An expense always has at least one journal (ORIGINAL).
 * If the expense is voided, a second journal (VOID_REVERSAL) is added.</p>
 */
@Schema(description = "A journal batch — two balanced ledger entries posted together")
public record ExpenseLedgerJournalDto(

        @Schema(description = "UUID that links both ledger entries in this batch")
        UUID journalId,

        @Schema(description = "ORIGINAL — the initial posting; VOID_REVERSAL — the reversal created when the expense was voided",
                example = "ORIGINAL", allowableValues = {"ORIGINAL", "VOID_REVERSAL"})
        String journalType,

        @Schema(description = "The two balanced ledger entries (CREDIT + DEBIT)")
        List<LedgerEntrySummaryDto> entries
) {
}
