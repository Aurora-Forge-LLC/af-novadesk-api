package com.af.novadesk.api.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

/**
 * Information about an inter-entity transfer for reconciliation purposes
 * (LLR-FIN-02.4).
 *
 * <p><b>Single-record model note:</b> Currently, inter-entity transfers create
 * only one {@code CapitalInjection} record with both {@code sourceEntity} and
 * {@code targetEntity} populated. As a result, {@code sourceCapitalInjectionId}
 * equals {@code targetCapitalInjectionId} and {@code sourceJournalId} equals
 * {@code targetJournalId} — both fields refer to the same record and journal.
 * A future enhancement may split inter-entity transfers into two separate
 * {@code CapitalInjection} records (one per entity) to make these fields
 * distinct.</p>
 */
@Schema(description = "Inter-entity transfer correlation details")
public record InterEntityTransferDto(

        @Schema(description = "Unique transfer ID linking both entity ledgers")
        UUID transferId,

        @Schema(description = "Source entity code", example = "US")
        String sourceEntityCode,

        @Schema(description = "Target entity code", example = "INDIA")
        String targetEntityCode,

        @Schema(description = "Capital injection ID in the source entity ledger")
        UUID sourceCapitalInjectionId,

        @Schema(description = "Capital injection ID in the target entity ledger")
        UUID targetCapitalInjectionId,

        @Schema(description = "Journal ID in the source entity ledger")
        UUID sourceJournalId,

        @Schema(description = "Journal ID in the target entity ledger")
        UUID targetJournalId
) {
}
