package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.LedgerEntrySide;
import com.af.novadesk.api.common.entity.LedgerEntry;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.dto.EntitySummaryRow;
import com.af.novadesk.api.finance.dto.LedgerReportResponse;
import com.af.novadesk.api.finance.dto.LedgerReportRow;
import com.af.novadesk.api.finance.dto.MultiEntityConsolidatedReport;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.exception.EntityNotFoundException;
import com.af.novadesk.api.finance.repository.FinanceLedgerRepository;
import com.af.novadesk.api.common.repository.LegalEntityRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.LedgerReportService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Default implementation of {@link LedgerReportService}.
 *
 * <p>Supports single-entity reports with currency selector (USD vs local)
 * and multi-entity consolidated reports (always in USD) per LLR-FIN-04.5.
 */
@Service
@Transactional(readOnly = true)
public class LedgerReportServiceImpl implements LedgerReportService {

    private final FinanceLedgerRepository financeLedgerRepository;
    private final LegalEntityRepository legalEntityRepository;
    private final FinanceSecurityContext securityContext;

    public LedgerReportServiceImpl(
            FinanceLedgerRepository financeLedgerRepository,
            LegalEntityRepository legalEntityRepository,
            FinanceSecurityContext securityContext
    ) {
        this.financeLedgerRepository = financeLedgerRepository;
        this.legalEntityRepository = legalEntityRepository;
        this.securityContext = securityContext;
    }

    @Override
    public LedgerReportResponse generateLedgerReport(
            UUID entityId,
            LocalDate startDate,
            LocalDate endDate,
            String currency,
            String accountCode,
            int page,
            int size
    ) {
        LegalEntity entity = legalEntityRepository
                .findByIdAndOrganizationId(entityId, securityContext.getOrganizationId())
                .orElseThrow(() -> new EntityNotFoundException(entityId));

        boolean useUsd = resolveUseUsd(currency, entity.getBaseCurrency());

        LocalDateTime start = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime end = endDate != null ? endDate.atTime(LocalTime.MAX) : null;

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<LedgerEntry> entryPage = financeLedgerRepository.findAll(
                FinanceLedgerRepository.filterSpec(entityId, start, end, accountCode), pageRequest);

        List<LedgerReportRow> rows = entryPage.getContent().stream()
                .map(e -> toReportRow(e, useUsd))
                .toList();

        return new LedgerReportResponse(
                rows,
                entryPage.getNumber(),
                entryPage.getSize(),
                entryPage.getTotalElements(),
                entryPage.getTotalPages(),
                useUsd ? "USD" : entity.getBaseCurrency(),
                entity.getEntityCode(),
                entity.getEntityName(),
                entity.getBaseCurrency()
        );
    }

    @Override
    public MultiEntityConsolidatedReport generateConsolidatedReport(
            List<UUID> entityIds,
            LocalDate startDate,
            LocalDate endDate
    ) {
        if (entityIds == null || entityIds.isEmpty()) {
            throw new BadRequestException("At least one entity ID is required");
        }

        // Pass wide date range instead of null to avoid PostgreSQL type-inference errors
        LocalDateTime start = startDate != null
                ? startDate.atStartOfDay()
                : LocalDateTime.of(1970, 1, 1, 0, 0);
        LocalDateTime end = endDate != null
                ? endDate.atTime(LocalTime.MAX)
                : LocalDateTime.of(2099, 12, 31, 23, 59);

        List<Object[]> aggregates = financeLedgerRepository.aggregateByEntity(
                entityIds, start, end);

        // Build entity summary rows from aggregation results
        List<EntitySummaryRow> summaries = new ArrayList<>();
        BigDecimal grandTotalDebitsUsd = BigDecimal.ZERO;
        BigDecimal grandTotalCreditsUsd = BigDecimal.ZERO;
        BigDecimal grandTotalDebitsLocal = BigDecimal.ZERO;
        BigDecimal grandTotalCreditsLocal = BigDecimal.ZERO;

        // Group aggregation results by entity
        // Each row: [entityId, side, totalUsd, totalLocal]
        // We need to resolve entity names separately
        for (UUID entityId : entityIds) {
            BigDecimal debitsLocal = BigDecimal.ZERO;
            BigDecimal creditsLocal = BigDecimal.ZERO;
            BigDecimal debitsUsd = BigDecimal.ZERO;
            BigDecimal creditsUsd = BigDecimal.ZERO;

            for (Object[] row : aggregates) {
                UUID rowEntityId = (UUID) row[0];
                if (!rowEntityId.equals(entityId)) {
                    continue;
                }
                LedgerEntrySide side = (LedgerEntrySide) row[1];
                BigDecimal totalUsd = (BigDecimal) row[2];
                BigDecimal totalLocal = (BigDecimal) row[3];

                if (side == LedgerEntrySide.DEBIT) {
                    debitsLocal = debitsLocal.add(totalLocal);
                    debitsUsd = debitsUsd.add(totalUsd);
                } else {
                    creditsLocal = creditsLocal.add(totalLocal);
                    creditsUsd = creditsUsd.add(totalUsd);
                }
            }

            LegalEntity entity = legalEntityRepository
                    .findByIdAndOrganizationId(entityId, securityContext.getOrganizationId())
                    .orElse(null);
            String entityCode = entity != null ? entity.getEntityCode() : entityId.toString();
            String entityName = entity != null ? entity.getEntityName() : "Unknown";
            String baseCurrency = entity != null ? entity.getBaseCurrency() : "USD";

            summaries.add(new EntitySummaryRow(
                    entityCode, entityName, baseCurrency,
                    debitsLocal, creditsLocal, debitsUsd, creditsUsd));

            grandTotalDebitsUsd = grandTotalDebitsUsd.add(debitsUsd);
            grandTotalCreditsUsd = grandTotalCreditsUsd.add(creditsUsd);
            grandTotalDebitsLocal = grandTotalDebitsLocal.add(debitsLocal);
            grandTotalCreditsLocal = grandTotalCreditsLocal.add(creditsLocal);
        }

        BigDecimal netPositionUsd = grandTotalDebitsUsd.subtract(grandTotalCreditsUsd);

        return new MultiEntityConsolidatedReport(
                summaries,
                grandTotalDebitsUsd,
                grandTotalCreditsUsd,
                netPositionUsd,
                startDate,
                endDate
        );
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean resolveUseUsd(String currency, String entityBaseCurrency) {
        if (currency == null || currency.isBlank()) {
            return true; // default to USD
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        if ("USD".equals(normalized)) {
            return true;
        }
        if ("LOCAL".equals(normalized)) {
            return false;
        }
        // If they pass a specific currency code, compare to entity base
        return !entityBaseCurrency.equalsIgnoreCase(normalized);
    }

    private LedgerReportRow toReportRow(LedgerEntry entry, boolean useUsd) {
        BigDecimal amount = useUsd ? entry.getAmountUsd() : entry.getAmountLocal();
        String currency = useUsd ? "USD" : entry.getCurrencyLocal();

        return new LedgerReportRow(
                entry.getId(),
                entry.getCreatedAt() != null ? entry.getCreatedAt().toLocalDate() : null,
                entry.getAccountName(),
                entry.getAccountCode(),
                entry.getEntrySide(),
                amount,
                currency,
                entry.getDescription(),
                entry.getReferenceType(),
                entry.getReferenceId(),
                entry.getExchangeRateUsed(),
                entry.getRateWarning() != null && entry.getRateWarning()
        );
    }
}
