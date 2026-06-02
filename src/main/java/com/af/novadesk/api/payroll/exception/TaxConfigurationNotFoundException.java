package com.af.novadesk.api.payroll.exception;

import com.af.novadesk.api.payroll.constants.Jurisdiction;

import java.util.UUID;

/**
 * Thrown when a TaxConfiguration is not found for a given entity + jurisdiction.
 */
public class TaxConfigurationNotFoundException extends PayrollBaseException {

    public TaxConfigurationNotFoundException(UUID legalEntityId, Jurisdiction jurisdiction) {
        super("PAY_TC_001",
                String.format("Tax configuration not found for entity %s, jurisdiction %s",
                        legalEntityId, jurisdiction));
    }

    public TaxConfigurationNotFoundException(String message) {
        super("PAY_TC_001", message);
    }
}
