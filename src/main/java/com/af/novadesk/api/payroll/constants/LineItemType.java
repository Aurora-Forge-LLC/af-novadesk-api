package com.af.novadesk.api.payroll.constants;

/**
 * Classification of payslip line items (LLR-PAY-04.5).
 */
public enum LineItemType {
    EARNING,            // Gross salary component (Base, HRA, Transport, etc.)
    DEDUCTION,          // Employee deduction (Tax, SSF, PF, Professional Tax)
    EMPLOYER_EXPENSE    // Employer-side contribution (not deducted from net pay)
}
