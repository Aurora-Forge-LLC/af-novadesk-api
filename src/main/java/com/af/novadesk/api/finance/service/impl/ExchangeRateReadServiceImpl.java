package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.service.ExchangeRateReadService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class ExchangeRateReadServiceImpl implements ExchangeRateReadService {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateReadServiceImpl(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
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
     */
    @Override
    public List<ExchangeRateSummaryResponse> list(
            String sourceCurrency,
            String targetCurrency,
            LocalDate rateDate) {

        // Normalise currency codes to upper-case; leave null as-is so the
        // repository treats them as "no filter" (M1 + currency normalisation).
        String src = sourceCurrency != null ? sourceCurrency.trim().toUpperCase(Locale.ROOT) : null;
        String tgt = targetCurrency != null ? targetCurrency.trim().toUpperCase(Locale.ROOT) : null;

        List<ExchangeRate> rates = exchangeRateRepository.findByFilters(src, tgt, rateDate);
        return rates.stream().map(this::toSummary).toList();
    }

    @Override
    public ExchangeRateSummaryResponse getById(UUID id) {
        ExchangeRate rate = exchangeRateRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Exchange rate not found with id: " + id));
        return toSummary(rate);
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
                rate.getCreatedAt()
        );
    }
}
