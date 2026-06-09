package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;
import java.util.UUID;

/**
 * Thrown when a {@code FiscalYearSetting} is expected but has not yet
 * been initialised for the entity (e.g. queried before approval).
 */
public class FiscalYearSettingNotFoundException extends FinanceBaseException {
    public FiscalYearSettingNotFoundException(UUID entityId) {
        super("FIN_FISCAL_001",
                String.format("Fiscal year setting not found for entity: %s", entityId));
    }
}