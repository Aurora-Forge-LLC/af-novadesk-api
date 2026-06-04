package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;

import java.time.LocalDate;

/**
 * Outbox publisher contract for the {@link ExchangeRate} aggregate (LLR-FIN-02.3).
 * Each method persists an outbox event row inside the caller's active transaction.
 */
public interface ExchangeRateOutboxService {

    void publishSyncCompleted(ExchangeRate rate);

    void publishSyncFailed(String sourceCurrency, String targetCurrency,
                           LocalDate rateDate, String errorMessage, int retryCount);

    void publishManuallyUpdated(ExchangeRate rate, String updatedBy);

    void publishCsvImported(CsvUploadResponse summary, String uploadedBy);

    void publishCsvImportFailed(String fileName, String errorMessage, String uploadedBy);
}
