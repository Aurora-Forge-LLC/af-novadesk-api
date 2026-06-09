package com.af.novadesk.api.payroll.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Result of the {@link com.af.novadesk.api.payroll.service.LeaveRuleEngine}
 * validation. Tells the caller whether the leave request is allowed, partially
 * allowed (some days covered, rest unpaid), or rejected outright.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeaveValidationResult {

    /** Whether the full request can be granted. */
    private boolean allowed;

    /** True when some days can be covered but the rest must be unpaid. */
    private boolean partialAllowed;

    /** How many paid days can be granted from this policy. */
    private BigDecimal paidDaysAllowed;

    /** Shortfall that must be covered as unpaid leave. */
    private BigDecimal unpaidDaysNeeded;

    /** Current borrow limit (informational, for earned policies). */
    private BigDecimal borrowLimit;

    /** Human-readable rejection reason, set when {@link #allowed} is false. */
    private String rejectionReason;

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static LeaveValidationResult allowed(BigDecimal days, BigDecimal borrowLimit) {
        return LeaveValidationResult.builder()
                .allowed(true)
                .partialAllowed(false)
                .paidDaysAllowed(days)
                .unpaidDaysNeeded(BigDecimal.ZERO)
                .borrowLimit(borrowLimit)
                .build();
    }

    public static LeaveValidationResult partialAllowed(BigDecimal paid, BigDecimal unpaid) {
        return LeaveValidationResult.builder()
                .allowed(false)
                .partialAllowed(true)
                .paidDaysAllowed(paid)
                .unpaidDaysNeeded(unpaid)
                .borrowLimit(BigDecimal.ZERO)
                .rejectionReason(String.format(
                        "Insufficient balance: %.1f days available, %.1f days will be marked as unpaid.",
                        paid, unpaid))
                .build();
    }

    public static LeaveValidationResult rejected(String reason) {
        return LeaveValidationResult.builder()
                .allowed(false)
                .partialAllowed(false)
                .paidDaysAllowed(BigDecimal.ZERO)
                .unpaidDaysNeeded(BigDecimal.ZERO)
                .borrowLimit(BigDecimal.ZERO)
                .rejectionReason(reason)
                .build();
    }
}
