package com.af.novadesk.api.finance.constants;

/**
 * Functional roles that a financial account can play in the double-entry system.
 *
 * <p>These roles drive automatic account resolution during capital-injection
 * workflows (LLR-FIN-02) without requiring the caller to know specific account
 * codes.  Each entity has exactly one ACTIVE account per mandatory role.</p>
 *
 * <p>Role → AccountType mapping (for reference):</p>
 * <ul>
 *   <li>CASH, BANK_OPERATING               → ASSET</li>
 *   <li>FOUNDER_EQUITY                     → EQUITY</li>
 *   <li>LOAN_PAYABLE                       → LIABILITY</li>
 *   <li>GRANT_INCOME                       → REVENUE</li>
 *   <li>INTER_ENTITY_RECEIVABLE            → ASSET</li>
 *   <li>INTER_ENTITY_PAYABLE               → LIABILITY</li>
 * </ul>
 */
public enum AccountRole {
    /** Petty-cash / physical cash on hand.  Destination for cash injections. */
    CASH,

    /** Primary operating bank account.  Default injection destination. */
    BANK_OPERATING,

    /** Equity injected by founders.  Source account for FOUNDER_EQUITY funding. */
    FOUNDER_EQUITY,

    /** Loan liability account used when the funding source is a loan. */
    LOAN_PAYABLE,

    /** Income account used when the funding source is a grant. */
    GRANT_INCOME,

    /**
     * Asset account on the <em>sending</em> entity that records an amount owed
     * to it from another entity (LLR-FIN-02.4).
     */
    INTER_ENTITY_RECEIVABLE,

    /**
     * Liability account on the <em>receiving</em> entity that records the amount
     * it owes to the sending entity (LLR-FIN-02.4).
     */
    INTER_ENTITY_PAYABLE
}

