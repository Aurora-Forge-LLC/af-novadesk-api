package com.af.novadesk.api.finance.constants;
/**
 * Categories for default bank / cash accounts created on entity activation.
 * Maps to LLR-FIN-01.2: "Create default bank account records (Cash, Operating Account)".
 */
public enum BankAccountType {
    CASH,
    OPERATING,
    SAVINGS,
    PAYROLL
}
