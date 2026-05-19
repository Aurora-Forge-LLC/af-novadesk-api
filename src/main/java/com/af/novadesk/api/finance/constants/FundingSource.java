package com.af.novadesk.api.finance.constants;

/**
 * Permitted sources of capital for a funding / capital-injection transaction
 * (LLR-FIN-02.1).
 *
 * <p>The value drives automatic account resolution in
 * {@code CapitalInjectionService}:</p>
 * <ul>
 *   <li>{@code FOUNDER_EQUITY}       → source account role: FOUNDER_EQUITY</li>
 *   <li>{@code LOAN}                 → source account role: LOAN_PAYABLE</li>
 *   <li>{@code GRANT}                → source account role: GRANT_INCOME</li>
 *   <li>{@code INTER_ENTITY_TRANSFER}→ four-leg inter-entity journal (LLR-FIN-02.4)</li>
 * </ul>
 */
public enum FundingSource {
    /** Capital contributed directly by the company's founders. */
    FOUNDER_EQUITY,

    /** External loan proceeds deposited into the entity. */
    LOAN,

    /** Grant income received from a government or external body. */
    GRANT,

    /**
     * Cash transferred from another legal entity within the same group.
     * Triggers a four-legged inter-entity journal entry (LLR-FIN-02.4).
     */
    INTER_ENTITY_TRANSFER
}

