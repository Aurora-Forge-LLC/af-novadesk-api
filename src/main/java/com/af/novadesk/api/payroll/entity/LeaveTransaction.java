package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.payroll.constants.LeaveType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * Immutable audit trail record for every leave balance change (LLR-PAY-01.4).
 * Created when:
 * <ul>
 *   <li>A leave request is approved (deduction)</li>
 *   <li>A leave request is cancelled before start date (restoration)</li>
 *   <li>Leave balances are allocated on onboarding or fiscal year rollover</li>
 * </ul>
 *
 * <p>{@code daysChange} is positive for deductions, negative for restorations.</p>
 */
@Entity
@Table(name = "pr_leave_transactions", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "leave_request_id", name = "idx_lt_request_id"),
        @Index(columnList = "employee_id, created_at", name = "idx_lt_employee_date")
    })
@Filter(name = "organizationFilter",
    condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"leaveRequest", "legalEntity", "employee"})
public class LeaveTransaction extends AbstractEntity {

    /** The leave request that caused this transaction (null for initial allocations). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "leave_request_id",
        foreignKey = @ForeignKey(name = "fk_lt_leave_request"))
    private LeaveRequest leaveRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lt_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lt_employee"))
    @NotNull(message = "Employee is required")
    private Employee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    @NotNull
    private LeaveType leaveType;

    /** Positive = deduction, Negative = restoration. */
    @Column(name = "days_change", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal daysChange;

    @Column(name = "balance_before", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal balanceBefore;

    @Column(name = "balance_after", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal balanceAfter;

    @Column(name = "transaction_type", nullable = false, length = 30)
    @NotNull
    private String transactionType;             // DEDUCTION, RESTORATION, ALLOCATION

    @Column(name = "description", length = 500)
    private String description;
}
