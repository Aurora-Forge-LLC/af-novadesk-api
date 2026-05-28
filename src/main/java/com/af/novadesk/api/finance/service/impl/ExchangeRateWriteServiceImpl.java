package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.config.FundingProperties;
import com.af.novadesk.api.finance.constants.RateSource;
import com.af.novadesk.api.finance.dto.CsvRowError;
import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateCsvRow;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateRequest;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.CsvImportException;
import com.af.novadesk.api.finance.exception.ExchangeRateAlreadyExistsException;
import com.af.novadesk.api.finance.exception.ExchangeRateNotFoundException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.service.ExchangeRateOutboxService;
import com.af.novadesk.api.finance.service.ExchangeRateWriteService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Default implementation of {@link ExchangeRateWriteService}.
 *
 * <p>Handles manual rate CRUD and CSV import for air-gapped deployments.
 * All writes publish outbox events inside the same transaction.
 */
@Service
@Transactional
public class ExchangeRateWriteServiceImpl implements ExchangeRateWriteService {

    private static final Logger log = LoggerFactory.getLogger(ExchangeRateWriteServiceImpl.class);

    /** Matches "XXX-YYY" where X/Y are 3-char currency codes. */
    private static final Pattern CURRENCY_PAIR_PATTERN =
            Pattern.compile("^[A-Z]{3}-[A-Z]{3}$");

    /** Expected CSV date format. */
    private static final DateTimeFormatter CSV_DATE_FMT =
            DateTimeFormatter.ISO_LOCAL_DATE;

    private final ExchangeRateRepository exchangeRateRepository;
    private final ExchangeRateOutboxService outboxService;
    private final FundingProperties fundingProperties;

    public ExchangeRateWriteServiceImpl(
            ExchangeRateRepository exchangeRateRepository,
            ExchangeRateOutboxService outboxService,
            FundingProperties fundingProperties
    ) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.outboxService = outboxService;
        this.fundingProperties = fundingProperties;
    }

    // -------------------------------------------------------------------------
    // CRUD
    // -------------------------------------------------------------------------

    @Override
    public ExchangeRateDetailResponse create(ExchangeRateRequest request, String createdBy) {
        String src = normalizeCurrency(request.getSourceCurrency());
        String tgt = normalizeCurrency(request.getTargetCurrency());

        // Prevent duplicate (only among ACTIVE rates)
        exchangeRateRepository
                .findBySourceCurrencyAndTargetCurrencyAndRateDateAndStatus(src, tgt, request.getRateDate(), com.af.novadesk.api.common.constants.Status.ACTIVE)
                .ifPresent(existing -> {
                    throw new ExchangeRateAlreadyExistsException(
                            "Exchange rate already exists for " + src + " → " + tgt
                                    + " on " + request.getRateDate());
                });

        ExchangeRate rate = ExchangeRate.builder()
                .sourceCurrency(src)
                .targetCurrency(tgt)
                .rateDate(request.getRateDate())
                .exchangeRate(request.getExchangeRate())
                .rateSource(RateSource.MANUAL)
                .createdBy(createdBy)
                .build();

        ExchangeRate saved = exchangeRateRepository.save(rate);
        outboxService.publishManuallyUpdated(saved, createdBy);

        log.info("Manual exchange rate created: {} → {} on {} by {}",
                src, tgt, request.getRateDate(), createdBy);
        return toDetail(saved);
    }

    @Override
    public ExchangeRateDetailResponse update(UUID id, ExchangeRateRequest request) {
        ExchangeRate existing = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new ExchangeRateNotFoundException(id));

        String src = normalizeCurrency(request.getSourceCurrency());
        String tgt = normalizeCurrency(request.getTargetCurrency());

        // Currency pair is immutable — validate match
        if (!existing.getSourceCurrency().equals(src)
                || !existing.getTargetCurrency().equals(tgt)) {
            throw new BadRequestException(
                    "Currency pair cannot be changed. Existing: "
                            + existing.getSourceCurrency() + " → "
                            + existing.getTargetCurrency());
        }

        existing.setRateDate(request.getRateDate());
        existing.setExchangeRate(request.getExchangeRate());

        ExchangeRate saved = exchangeRateRepository.save(existing);
        outboxService.publishManuallyUpdated(saved,
                saved.getCreatedBy() != null ? saved.getCreatedBy() : "system");

        log.info("Exchange rate updated: {} → {} on {} (id={})",
                src, tgt, request.getRateDate(), id);
        return toDetail(saved);
    }

    @Override
    public void approve(UUID id, String approvedBy) {
        ExchangeRate rate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new ExchangeRateNotFoundException(id));

        rate.setApprovedBy(approvedBy);
        exchangeRateRepository.save(rate);

        outboxService.publishManuallyUpdated(rate, approvedBy);

        log.info("Exchange rate approved: {} → {} on {} by {}",
                rate.getSourceCurrency(), rate.getTargetCurrency(),
                rate.getRateDate(), approvedBy);
    }

    @Override
    public void softDelete(UUID id) {
        ExchangeRate rate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new ExchangeRateNotFoundException(id));

        rate.setStatus(com.af.novadesk.api.common.constants.Status.INACTIVE);
        exchangeRateRepository.save(rate);

        log.info("Exchange rate soft-deleted: {} → {} on {} (id={})",
                rate.getSourceCurrency(), rate.getTargetCurrency(),
                rate.getRateDate(), id);
    }

    // -------------------------------------------------------------------------
    // CSV Import (LLR-FIN-04.2)
    // -------------------------------------------------------------------------

    @Override
    public CsvUploadResponse importCsv(MultipartFile file, String uploadedBy) {
        if (file == null || file.isEmpty()) {
            throw new CsvImportException("Uploaded file is empty");
        }

        List<ExchangeRateCsvRow> parsedRows;
        try {
            parsedRows = parseCsv(file);
        } catch (CsvImportException e) {
            outboxService.publishCsvImportFailed(
                    file.getOriginalFilename(), e.getMessage(), uploadedBy);
            throw e;
        }

        if (parsedRows.isEmpty()) {
            throw new CsvImportException("CSV file contains no data rows");
        }

        List<CsvRowError> errors = new ArrayList<>();
        int successCount = 0;
        int skippedCount = 0;

        // Validate all rows first, then persist valid ones
        for (ExchangeRateCsvRow row : parsedRows) {
            List<String> rowErrors = validateCsvRow(row);
            if (!rowErrors.isEmpty()) {
                for (String err : rowErrors) {
                    errors.add(new CsvRowError(row.lineNumber(), err));
                }
                continue;
            }

            // Duplicate check (only among ACTIVE rates)
            boolean exists = exchangeRateRepository
                    .findBySourceCurrencyAndTargetCurrencyAndRateDateAndStatus(
                            row.sourceCurrency(), row.targetCurrency(), row.date(), com.af.novadesk.api.common.constants.Status.ACTIVE)
                    .isPresent();
            if (exists) {
                skippedCount++;
                continue;
            }

            // Persist
            ExchangeRate rate = ExchangeRate.builder()
                    .sourceCurrency(row.sourceCurrency())
                    .targetCurrency(row.targetCurrency())
                    .rateDate(row.date())
                    .exchangeRate(row.rate())
                    .rateSource(RateSource.MANUAL)
                    .createdBy(uploadedBy)
                    .build();
            exchangeRateRepository.save(rate);
            successCount++;
        }

        CsvUploadResponse response = new CsvUploadResponse(
                parsedRows.size(), successCount, skippedCount, errors.size(), errors);

        if (successCount > 0 || skippedCount > 0) {
            outboxService.publishCsvImported(response, uploadedBy);
        }

        log.info("CSV import complete: {} total, {} success, {} skipped, {} errors",
                parsedRows.size(), successCount, skippedCount, errors.size());
        return response;
    }

    // -------------------------------------------------------------------------
    // CSV Parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the uploaded CSV file into a list of {@link ExchangeRateCsvRow}.
     *
     * <p>Expected format: {@code date, currency_pair, rate}
     * <br>Example: {@code 2026-04-23, INR-USD, 0.012045}
     */
    private List<ExchangeRateCsvRow> parseCsv(MultipartFile file) {
        List<ExchangeRateCsvRow> rows = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream()))) {

            String line;
            int lineNumber = 0;

            while ((line = reader.readLine()) != null) {
                lineNumber++;

                // Skip blank lines
                String trimmed = line.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                // Skip header row (starts with "date" or "#")
                if (lineNumber == 1 && (trimmed.toLowerCase().startsWith("date")
                        || trimmed.startsWith("#"))) {
                    continue;
                }

                String[] parts = trimmed.split(",", 3);
                if (parts.length != 3) {
                    throw new CsvImportException(
                            "Line " + lineNumber + ": expected 3 comma-separated values "
                                    + "(date, currency_pair, rate), got " + parts.length);
                }

                String dateStr = parts[0].trim();
                String pairStr = parts[1].trim();
                String rateStr = parts[2].trim();

                // Parse date
                LocalDate date;
                try {
                    date = LocalDate.parse(dateStr, CSV_DATE_FMT);
                } catch (DateTimeParseException e) {
                    throw new CsvImportException(
                            "Line " + lineNumber + ": invalid date '" + dateStr
                                    + "' — expected ISO format (YYYY-MM-DD)");
                }

                // Parse currency pair
                String pairUpper = pairStr.toUpperCase(Locale.ROOT);
                if (!CURRENCY_PAIR_PATTERN.matcher(pairUpper).matches()) {
                    throw new CsvImportException(
                            "Line " + lineNumber + ": invalid currency pair '" + pairStr
                                    + "' — expected format XXX-YYY (e.g., INR-USD)");
                }
                String sourceCurrency = pairUpper.substring(0, 3);
                String targetCurrency = pairUpper.substring(4);

                // Parse rate
                BigDecimal rate;
                try {
                    rate = new BigDecimal(rateStr);
                } catch (NumberFormatException e) {
                    throw new CsvImportException(
                            "Line " + lineNumber + ": invalid rate '" + rateStr
                                    + "' — expected a decimal number");
                }

                rows.add(new ExchangeRateCsvRow(
                        date, sourceCurrency, targetCurrency, rate, lineNumber));
            }
        } catch (CsvImportException e) {
            throw e;
        } catch (Exception e) {
            throw new CsvImportException(
                    "Failed to read CSV file: " + e.getMessage(), e);
        }

        return rows;
    }

    /**
     * Validates a single parsed CSV row.
     *
     * @return list of error messages; empty means valid
     */
    private List<String> validateCsvRow(ExchangeRateCsvRow row) {
        List<String> errors = new ArrayList<>();

        // Date must not be in the future
        if (row.date().isAfter(LocalDate.now())) {
            errors.add("Rate date cannot be in the future");
        }

        // Rate must be positive
        if (row.rate().signum() <= 0) {
            errors.add("Exchange rate must be positive");
        } else {
            // Rate must not exceed configured ceiling
            BigDecimal ceiling = fundingProperties.maxManualExchangeRate();
            if (row.rate().compareTo(ceiling) > 0) {
                errors.add("Exchange rate " + row.rate()
                        + " exceeds the allowed ceiling of " + ceiling);
            }
        }

        return errors;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new BadRequestException("Currency code must not be blank");
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    private ExchangeRateDetailResponse toDetail(ExchangeRate rate) {
        return new ExchangeRateDetailResponse(
                rate.getId(),
                rate.getSourceCurrency(),
                rate.getTargetCurrency(),
                rate.getRateDate(),
                rate.getExchangeRate(),
                rate.getRateSource(),
                rate.getStatus(),
                rate.getCreatedBy(),
                rate.getApprovedBy(),
                rate.getCreatedAt(),
                rate.getUpdatedAt()
        );
    }
}
