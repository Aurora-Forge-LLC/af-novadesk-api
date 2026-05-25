package com.af.novadesk.api.expense.constants;

/**
 * Method used to settle an {@link com.af.novadesk.api.expense.entity.ExpenseTransaction}.
 *
 * <p>Drives the payment method dropdown on the expense entry form (LLR-FIN-03.1).</p>
 */
public enum PaymentMethod {

    /** Wire or ACH transfer from a company bank account. */
    BANK_TRANSFER,

    /** Charged to a company credit card. */
    CREDIT_CARD,

    /** Physical cash payment. */
    CASH,

    /** Paper or electronic check issued to the vendor. */
    CHECK
}
