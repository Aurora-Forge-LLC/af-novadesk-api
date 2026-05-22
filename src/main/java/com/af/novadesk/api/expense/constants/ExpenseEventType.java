package com.af.novadesk.api.expense.constants;

/**
 * Domain events produced by the {@link com.af.novadesk.api.expense.entity.ExpenseTransaction} aggregate.
 *
 * <p>Each value documents its payload contract in a Javadoc comment so the
 * event catalogue lives alongside the code.</p>
 */
public enum ExpenseEventType {

    /**
     * Fired when a finance operator records a new expense and double-entry
     * ledger entries are successfully posted (LLR-FIN-03.2).
     * Triggers: audit trail entry, Finance team notification.
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code transactionId}       – UUID of the created expense transaction</li>
     *   <li>{@code legalEntityId}       – entity the expense belongs to</li>
     *   <li>{@code vendorId}            – UUID of the vendor paid</li>
     *   <li>{@code vendorName}          – denormalised vendor name for quick display</li>
     *   <li>{@code amount}              – transaction amount</li>
     *   <li>{@code currencyCode}        – ISO 4217 currency code</li>
     *   <li>{@code expenseDate}         – ISO-8601 date of the expense</li>
     *   <li>{@code createdByAuthUserId} – JWT sub of the submitting user</li>
     *   <li>{@code organizationId}      – org scope of the request</li>
     * </ul>
     * </p>
     */
    EXPENSE_CREATED,

    /**
     * Fired when a finance operator voids a previously posted expense (LLR-FIN-03).
     * Triggers: reversal ledger entries, audit trail entry.
     *
     * <p>Payload fields:
     * <ul>
     *   <li>{@code transactionId}      – UUID of the voided expense transaction</li>
     *   <li>{@code legalEntityId}      – entity the expense belongs to</li>
     *   <li>{@code voidedByAuthUserId} – JWT sub of the user who voided it</li>
     *   <li>{@code voidedAt}           – ISO-8601 datetime of the void action</li>
     *   <li>{@code organizationId}     – org scope</li>
     * </ul>
     * </p>
     */
    EXPENSE_VOIDED
}
