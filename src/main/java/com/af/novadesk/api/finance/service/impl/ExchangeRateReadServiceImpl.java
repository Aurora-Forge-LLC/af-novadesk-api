package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.finance.constants.ExchangeRateApprovalStatus;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.ExchangeRateNotFoundException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.ExchangeRateReadService;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Read-only service for exchange rates.
 *
 * <p>All queries automatically apply the {@code organizationFilter} Hibernate
 * filter to prevent cross-org data leakage. The filter is enabled per-request
 * using the organization ID from {@link FinanceSecurityContext}.</p>
 */
@Service
public class ExchangeRateReadServiceImpl implements ExchangeRateReadService {

    private final ExchangeRateRepository exchangeRateRepository;
    private final FinanceSecurityContext securityContext;
    private final EntityManager entityManager;

    public ExchangeRateReadServiceImpl(
            ExchangeRateRepository exchangeRateRepository,
            FinanceSecurityContext securityContext,
            EntityManager entityManager
    ) {
        this.exchangeRateRepository = exchangeRateRepository;
        this.securityContext = securityContext;
        this.entityManager = entityManager;
    }

    /**
     * Enables the Hibernate {@code organizationFilter} for the current session.
     */
    private void enableOrgFilter() {
        UUID orgId = securityContext.getOrganizationId();
        entityManager.unwrap(Session.class)
                .enableFilter("organizationFilter")
                .setParameter("orgId", orgId);
    }

    /**
     * Lists exchange rates, optionally filtered by any combination of
     * {@code sourceCurrency}, {@code targetCurrency}, and {@code rateDate}.
     *
     * <p>Currency codes are normalised to upper-case before lookup so that a
     * caller passing {@code "usd"} receives the same results as {@code "USD"}.
     * All three filters are independent — partial filter sets (e.g. only
     * {@code sourceCurrency}) are honoured correctly without silently falling
     * back to an unfiltered full-table scan (M1).</p>
     *
     * <p>Results are automatically scoped to the caller's organisation via the
     * Hibernate {@code organizationFilter}.</p>
     */
    @Override
    public PageResponse<ExchangeRateSummaryResponse> list(
            String sourceCurrency, String targetCurrency, LocalDate rateDate,
            LocalDate fromDate, LocalDate toDate,
            ExchangeRateApprovalStatus approvalStatus,
            int page, int size) {

        String src = sourceCurrency != null ? sourceCurrency.trim().toUpperCase(Locale.ROOT) : null;
        String tgt = targetCurrency != null ? targetCurrency.trim().toUpperCase(Locale.ROOT) : null;
        UUID orgId = securityContext.getOrganizationId();

        enableOrgFilter();

        Specification<ExchangeRate> spec = ExchangeRateRepository.filterSpec(
                orgId, src, tgt, rateDate, fromDate, toDate, approvalStatus);
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "rateDate"));
        return PageResponse.of(exchangeRateRepository.findAll(spec, pageable).map(this::toSummary));
    }

    @Override
    public ExchangeRateDetailResponse getById(UUID id) {
        enableOrgFilter();

        ExchangeRate rate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new ExchangeRateNotFoundException(id));
        return toDetail(rate);
    }

    private ExchangeRateSummaryResponse toSummary(ExchangeRate rate) {
        return new ExchangeRateSummaryResponse(
                rate.getId(),
                rate.getSourceCurrency(),
                rate.getTargetCurrency(),
                rate.getRateDate(),
                rate.getExchangeRate(),
                rate.getRateSource(),
                rate.getStatus(),
                rate.getCreatedAt(),
                rate.getCreatedBy(),
                rate.getUpdatedAt(),
                rate.getApprovedBy(),
                rate.getApprovedAt(),
                rate.getApprovalStatus()
        );
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
                rate.getApprovedAt(),
                rate.getCreatedAt(),
                rate.getUpdatedAt(),
                rate.getApprovalStatus()
        );
    }
}
