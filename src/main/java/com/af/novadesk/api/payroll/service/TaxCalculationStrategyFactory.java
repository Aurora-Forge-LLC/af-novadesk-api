package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.af.novadesk.api.payroll.exception.TaxConfigurationNotFoundException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory that selects the appropriate {@link TaxCalculationStrategy}
 * based on the entity's jurisdiction.
 */
@Component
public class TaxCalculationStrategyFactory {

    private final Map<Jurisdiction, TaxCalculationStrategy> strategies;

    public TaxCalculationStrategyFactory(List<TaxCalculationStrategy> strategyList) {
        this.strategies = strategyList.stream()
                .collect(Collectors.toMap(
                        TaxCalculationStrategy::getSupportedJurisdiction,
                        Function.identity()));
    }

    public TaxCalculationStrategy getStrategy(Jurisdiction jurisdiction) {
        TaxCalculationStrategy strategy = strategies.get(jurisdiction);
        if (strategy == null) {
            throw new TaxConfigurationNotFoundException(
                    "No tax calculation strategy configured for jurisdiction: " + jurisdiction);
        }
        return strategy;
    }
}
