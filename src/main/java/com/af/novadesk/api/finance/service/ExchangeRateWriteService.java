package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateRequest;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Write operations for exchange rate management (LLR-FIN-04.1 / LLR-FIN-04.2).
 *
 * <p>Complements {@link ExchangeRateReadService} which handles read-only queries.
 * All write operations publish outbox events via {@code ExchangeRateOutboxService}
 * inside the same transaction for modular-monolith consistency.</p>
 */
public interface ExchangeRateWriteService {

    /**
     * Creates a single manual exchange rate entry.
     *
     * @param request   rate details
     * @param createdBy authenticated user identity from security context
     * @return the persisted rate as a detail response
     */
    ExchangeRateDetailResponse create(ExchangeRateRequest request, String createdBy);

    /**
     * Updates an existing exchange rate. Only the rate value and rate date
     * are mutable; the currency pair is immutable.
     *
     * @param id      UUID of the rate to update
     * @param request updated rate details
     * @return the updated rate as a detail response
     */
    ExchangeRateDetailResponse update(UUID id, ExchangeRateRequest request);

    /**
     * Records an approver for a manually-entered rate (LLR-FIN-04.1).
     * The approve action is separate from creation to support workflows
     * where the submitter and approver are different users.
     *
     * @param id         UUID of the rate to approve
     * @param approvedBy authenticated user identity performing the approval
     */
    void approve(UUID id, String approvedBy);

    /**
     * Soft-deletes an exchange rate by setting its status to {@code INACTIVE}.
     *
     * @param id UUID of the rate to deactivate
     */
    void softDelete(UUID id);

    /**
     * Imports exchange rates from a CSV file (LLR-FIN-04.2).
     *
     * <p>CSV format: {@code date, currency_pair, rate}
     * <br>Example: {@code 2026-04-23, INR-USD, 0.012045}</p>
     *
     * <p>Duplicate rows (same source+target+date) are skipped and counted
     * in the response. Validation errors are collected and reported per-row.</p>
     *
     * @param file       the uploaded CSV file (multipart)
     * @param uploadedBy authenticated user identity
     * @return summary of rows imported, skipped, and errored
     */
    CsvUploadResponse importCsv(MultipartFile file, String uploadedBy);
}
