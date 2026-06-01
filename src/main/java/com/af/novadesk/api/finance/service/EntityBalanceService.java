package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.EntityBalanceResponse;

/**
 * Read-only service that computes the financial balance / available-capital
 * snapshot for a single legal entity (LLR-FIN-02.5).
 *
 * <p>Aggregates all POSTED capital injections and expenses, then computes
 * {@code netAvailable = totalCapitalInjected - totalExpenses} in both the
 * entity's local currency and USD.</p>
 */
public interface EntityBalanceService {

    /**
     * Returns the financial balance snapshot for the entity identified by its
     * entity code.
     *
     * @param entityCode the legal entity code (e.g. "INDIA", "US")
     * @return balance snapshot with capital-injection totals, expense totals,
     *         and net available capital in both local currency and USD
     */
    EntityBalanceResponse getEntityBalance(String entityCode);
}
