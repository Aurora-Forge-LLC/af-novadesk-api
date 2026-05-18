package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import com.af.novadesk.api.finance.entity.ExchangeRate;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.ExchangeRateRepository;
import com.af.novadesk.api.finance.service.ExchangeRateReadService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
public class ExchangeRateReadServiceImpl implements ExchangeRateReadService {

    private final ExchangeRateRepository exchangeRateRepository;

    public ExchangeRateReadServiceImpl(ExchangeRateRepository exchangeRateRepository) {
        this.exchangeRateRepository = exchangeRateRepository;
    }

    @Override
    public List<ExchangeRateSummaryResponse> list(String sourceCurrency, String targetCurrency, LocalDate rateDate) {
        List<ExchangeRate> rates;
        if (sourceCurrency != null && targetCurrency != null && rateDate != null) {
            rates = exchangeRateRepository
                    .findBySourceCurrencyAndTargetCurrencyAndRateDate(sourceCurrency, targetCurrency, rateDate)
                    .map(List::of)
                    .orElse(List.of());
        } else {
            rates = exchangeRateRepository.findAll();
        }

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

