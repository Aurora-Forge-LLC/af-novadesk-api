package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.payroll.constants.FlagAction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Review queue item for an employee whose payroll requires executive action
 * due to unpaid leave or unauthorized absences (LLR-PAY-02.2–02.3).
 *
 * <p>Executive can Waive (full salary) or Prorate (adjusted salary).
 * Records the action taken for audit trail.</p>
 */
@Entity
@Table(name = "pr_payroll_flagged_employees", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"payroll_batch_id", "employee_id"},
            name = "uk_pfe_batch_employee")
    },
    indexes = {
        @Index(columnList = "flag_action, created_at", name = "idx_pfe_action_created")
    })
@Filter(name = "organizationFilter",
    condition = "payroll_batch_id IN (SELECT pb.id FROM af_novadesk.pr_payroll_batches pb " +
                "JOIN af_novadesk.legal_entities le ON pb.legal_entity_id = le.id " +
                "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"payrollBatch", "employee", "actionBy"})
public class PayrollFlaggedEmployee extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_batch_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_pfe_batch"))
    @NotNull(message = "Payroll batch is required")
    private PayrollBatch payrollBatch;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_pfe_employee"))
    @NotNull(message = "Employee is required")
    private CmEmployee employee;

    // -------------------------------------------------------------------------
    // Flag Reason
    // -------------------------------------------------------------------------

    @Column(name = "unpaid_leave_days", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    @NotNull
    private BigDecimal unpaidLeaveDays = BigDecimal.ZERO;

    @Column(name = "unauthorized_absence_days", nullable = false, precision = 4, scale = 1)
    @Builder.Default
    @NotNull
    private BigDecimal unauthorizedAbsenceDays = BigDecimal.ZERO;

    @Column(name = "flag_reason", nullable = false, length = 500)
    @NotNull
    private String flagReason;

    // -------------------------------------------------------------------------
    // Salary Details
    // -------------------------------------------------------------------------

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal baseSalary;

    @Column(name = "calculated_salary", precision = 19, scale = 4)
    private BigDecimal calculatedSalary;        // Prorated amount after action

    @Column(name = "total_working_days", nullable = false)
    @NotNull
    private Integer totalWorkingDays;

    @Column(name = "days_worked", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal daysWorked;

    // -------------------------------------------------------------------------
    // Executive Action
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_action", nullable = false, length = 20)
    @Builder.Default
    @NotNull
    private FlagAction flagAction = FlagAction.PENDING_REVIEW;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "action_by",
        foreignKey = @ForeignKey(name = "fk_pfe_action_by"))
    private CmEmployee actionBy;                  // Executive who took action (MANAGER case)

    /**
     * Stores the authUserId (from JWT {@code sub}) when the actor is not a
     * {@link CmEmployee} — e.g. SUPER_ADMIN who has not been onboarded as
     * an employee of the entity.
     */
    @Column(name = "action_by_auth_user_id")
    private UUID actionByAuthUserId;

    @Column(name = "action_at")
    private LocalDateTime actionAt;

    @Column(name = "action_reason", length = 500)
    private String actionReason;                // Required for WAIVED
}
