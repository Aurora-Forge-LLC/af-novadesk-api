package com.af.novadesk.api.finance.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised configuration for the Bank Reconciliation sub-domain (LLR-BNK-01).
 *
 * <p>Bound from the {@code finance.bank-reconciliation} prefix in application YAML.</p>
 *
 * @param maxFileSizeBytes       Maximum allowed upload file size in bytes (default 10 MB).
 * @param allowedFileTypes       Comma-separated list of allowed file extensions (default CSV,XLSX,XLS).
 * @param parseBatchSize         Number of transactions to persist per batch insert (default 500).
 */
@ConfigurationProperties(prefix = "finance.bank-reconciliation")
public record BankReconciliationProperties(
        long maxFileSizeBytes,
        String allowedFileTypes,
        int parseBatchSize
) {
    /** Default constructor — used when no YAML overrides are provided. */
    public BankReconciliationProperties() {
        this(10_485_760L, "CSV,XLSX,XLS,PDF", 500);
    }
}
