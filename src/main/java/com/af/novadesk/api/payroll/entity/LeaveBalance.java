package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.payroll.constants.LeaveType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks an employee's leave balance for a specific {@link LeavePolicy} and
 * {@link FiscalYearSetting} (LLR-PAY-01.1). Created automatically on employee
 * onboarding. Aligned to the entity's fiscal year — when the fiscal year
 * rolls over, a scheduled job proactively creates new balance records.
 *
 * <p>The composite unique constraint ensures one balance record per employee,
 * per leave policy, per fiscal year.</p>
 *
 * <p>The legacy {@link LeaveType} field is retained for backward compatibility
 * but is deprecated in favor of {@link #leavePolicy}.</p>
 */
@Entity
@Table(name = "pr_leave_balances", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "leave_policy_id", "fiscal_year_setting_id"},
            name = "uk_lb_employee_policy_fiscal")
    },
    indexes = {
        @Index(columnList = "employee_id", name = "idx_lb_employee_id"),
        @Index(columnList = "legal_entity_id", name = "idx_lb_entity_id"),
        @Index(columnList = "leave_policy_id", name = "idx_lb_policy_id")
    })
@Filter(name = "organizationFilter",
    condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "employee", "fiscalYearSetting", "leavePolicy"})
public class LeaveBalance extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lb_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lb_employee"))
    @NotNull(message = "Employee is required")
    private Employee employee;

    /** @deprecated Use {@link #leavePolicy} instead. Retained for backward compatibility. */
    @Deprecated
    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", length = 20)
    private LeaveType leaveType;

    /** The configurable leave policy this balance tracks. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_policy_id",
        foreignKey = @ForeignKey(name = "fk_lb_leave_policy"))
    private LeavePolicy leavePolicy;

    /** Annual allocation for this leave type (from the policy). */
    @Column(name = "total_allocated", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal totalAllocated;

    @Column(name = "used_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal usedDays = BigDecimal.ZERO;

    /** Days reserved by pending (not yet approved) leave requests. */
    @Column(name = "pending_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal pendingDays = BigDecimal.ZERO;

    /**
     * Computed: earnedDays - usedDays - pendingDays (for earned policies)
     * or totalAllocated - usedDays - pendingDays (for upfront policies).
     * May go negative when borrowing against future accruals on earned leave
     * policies. The negative value is repaid through future monthly accruals.
     */
    @Column(name = "available_days", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal availableDays;

    /**
     * Cumulative days earned through monthly accrual. Only relevant for
     * earned leave policies. For non-earned policies, this equals totalAllocated.
     */
    @Column(name = "earned_days", nullable = false, precision = 5, scale = 1)
    @Builder.Default
    private BigDecimal earnedDays = BigDecimal.ZERO;

    /** The employee's hire date — accrual starts from this date. */
    @Column(name = "accrual_start_date", nullable = false)
    @NotNull
    private LocalDate accrualStartDate;

    /**
     * Fiscal year this balance applies to. Leave resets are aligned to the
     * entity's fiscal year as defined in {@link FiscalYearSetting}.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "fiscal_year_setting_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lb_fiscal_year"))
    @NotNull(message = "Fiscal year setting is required")
    private FiscalYearSetting fiscalYearSetting;
}
