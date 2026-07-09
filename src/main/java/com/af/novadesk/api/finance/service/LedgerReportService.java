package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.finance.dto.LedgerReportResponse;
import com.af.novadesk.api.finance.dto.MultiEntityConsolidatedReport;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Read-only reporting service for financial ledger reports (LLR-FIN-04.5).
 *
 * <p>Supports single-entity reports with currency selector (USD vs local)
 * and multi-entity consolidated reports (always in USD).
 */
public interface LedgerReportService {

    /**
     * Generates a paginated ledger report for a single legal entity.
     *
     * @param entityId   the legal entity UUID
     * @param startDate  optional start of date range (inclusive)
     * @param endDate    optional end of date range (inclusive)
     * @param currency   "USD" or "LOCAL" — which amount column to display
     * @param accountId  optional filter by a specific account
     * @param page       0-based page number
     * @param size       page size
     * @return paginated report response
     */
    LedgerReportResponse generateLedgerReport(
            UUID entityId,
            LocalDate startDate,
            LocalDate endDate,
            String currency,
            UUID accountId,
            int page,
            int size,
            String q,
            LedgerEntrySide entrySide,
            String referenceType,
            String entryCurrency
    );

    /**
     * Generates a consolidated multi-entity report always denominated in USD.
     *
     * @param entityIds list of legal entity UUIDs to include
     * @param startDate optional start of date range (inclusive)
     * @param endDate   optional end of date range (inclusive)
     * @return consolidated report with per-entity breakdowns and grand totals
     */
    MultiEntityConsolidatedReport generateConsolidatedReport(
            List<UUID> entityIds,
            LocalDate startDate,
            LocalDate endDate
    );
}
