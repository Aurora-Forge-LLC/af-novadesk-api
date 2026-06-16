package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Configurable leave policy per entity — the core of the leave rule engine.
 * Each entity can define multiple named leave types (e.g. "Personal", "Sick",
 * "PTO", "Maternity") with per-policy rules for payment type, yearly allocation,
 * earning schedule, and borrowing limits.
 *
 * <p>Replaces the hardcoded {@link com.af.novadesk.api.payroll.constants.LeaveType}
 * enum with a fully dynamic, database-driven rule set.</p>
 */
@Entity
@Table(name = "pr_leave_policies", schema = "af_novadesk",
        indexes = {
                @Index(columnList = "legal_entity_id", name = "idx_lp_entity_id"),
                @Index(columnList = "legal_entity_id, status", name = "idx_lp_entity_status")
        })
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity"})
public class LeavePolicy extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_lp_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    /** Human-readable name, e.g. "Personal", "Sick", "Maternity". */
    @Column(name = "name", nullable = false, length = 100)
    @NotBlank(message = "Policy name is required")
    private String name;

    /** Whether this leave is PAID or UNPAID. */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_type", nullable = false, length = 10)
    @NotNull(message = "Payment type is required")
    private LeavePaymentType paymentType;

    /** Yearly allocation in days. Set to 0 when {@link #isUnlimited} is true. */
    @Column(name = "allowed_days", nullable = false)
    @NotNull(message = "Allowed days is required")
    private Integer allowedDays;

    /** If true, allocation is treated as unlimited (e.g. for unpaid leave). */
    @Column(name = "is_unlimited", nullable = false)
    @Builder.Default
    private Boolean isUnlimited = false;

    /**
     * If true, days accrue monthly ({@code allowedDays / 12}) rather than
     * being fully available upfront. Enables borrowing rules.
     */
    @Column(name = "is_earned", nullable = false)
    @Builder.Default
    private Boolean isEarned = false;

    /**
     * Multiplier for borrowing unearned leave. Only applicable when
     * {@link #isEarned} is true. E.g. a value of 2 means the employee
     * can borrow up to 2× their currently earned balance.
     */
    @Column(name = "borrow_multiple")
    private Integer borrowMultiple;

    /** AuthHub user ID of the admin/manager who created this policy. */
    @Column(name = "created_by", nullable = false)
    @NotNull(message = "Creator ID is required")
    private UUID createdBy;

    // -------------------------------------------------------------------------
    // Computed helpers (not persisted)
    // -------------------------------------------------------------------------

    /**
     * Monthly accrual rate for earned leave.
     * Returns 0 for non-earned or unlimited policies.
     */
    public BigDecimal getMonthlyAccrualRate() {
        if (!isEarned || isUnlimited || allowedDays == null || allowedDays == 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(allowedDays)
                .divide(BigDecimal.valueOf(12), 1, RoundingMode.HALF_UP);
    }

    /**
     * Allowed days as a BigDecimal for balance calculations.
     * Returns a sentinel 999 for unlimited policies.
     */
    public BigDecimal getAllowedDaysAsDecimal() {
        if (isUnlimited) {
            return new BigDecimal("999");
        }
        return BigDecimal.valueOf(allowedDays);
    }

    /**
     * The effective borrow multiple, returning 0 when borrowing is not applicable.
     */
    public int getEffectiveBorrowMultiple() {
        if (!isEarned || borrowMultiple == null) {
            return 0;
        }
        return borrowMultiple;
    }
}
