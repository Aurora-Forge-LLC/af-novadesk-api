package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.payroll.constants.LeaveType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Tracks an employee's leave balance for a specific {@link LeaveType} and
 * {@link FiscalYearSetting} (LLR-PAY-01.1). Created automatically on employee
 * onboarding. Aligned to the entity's fiscal year — when the fiscal year
 * rolls over, a scheduled job proactively creates new balance records.
 *
 * <p>The composite unique constraint ensures one balance record per employee,
 * per leave type, per fiscal year.</p>
 */
@Entity
@Table(name = "pr_leave_balances", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "leave_type", "fiscal_year_setting_id"},
            name = "uk_lb_employee_type_fiscal")
    },
    indexes = {
        @Index(columnList = "employee_id",   name = "idx_lb_employee_id"),
        @Index(columnList = "organization_id", name = "idx_lb_org_id")
    })
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"employee", "fiscalYearSetting"})
public class LeaveBalance extends AbstractEntity {

    /** Org scope — leave follows the employee across all entities in the org. */
    @Column(name = "organization_id", nullable = false)
    @NotNull
    private java.util.UUID organizationId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lb_employee"))
    @NotNull(message = "Employee is required")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    /** Annual allocation for this leave type (e.g. 10 for Paid). */
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

    /** Computed: totalAllocated - usedDays - pendingDays. */
    @Column(name = "available_days", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal availableDays;

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
