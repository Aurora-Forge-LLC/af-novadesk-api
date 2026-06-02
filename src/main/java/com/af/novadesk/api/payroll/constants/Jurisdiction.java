package com.af.novadesk.api.payroll.constants;

/**
 * Tax jurisdictions supported by the payroll engine (LLR-PAY-04).
 * Each jurisdiction maps to a specific {@code TaxCalculationStrategy} implementation.
 */
public enum Jurisdiction {
    NEPAL,      // IRD progressive tax + 31% SSF
    INDIA,      // Income Tax slabs + 12% PF + Professional Tax
    USA         // Federal/State tax + Social Security + Medicare (future)
}
