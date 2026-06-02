package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.payroll.constants.LeaveRequestStatus;
import com.af.novadesk.api.payroll.constants.LeaveType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Aggregate root for the leave management workflow (LLR-PAY-01.2–01.5).
 *
 * <p>Represents a single leave request submitted by an employee. The request
 * follows an approval hierarchy: Manager → HR (>5 consecutive days) →
 * Executive (Unpaid leaves). Balance snapshots are stored at request time
 * for audit trail integrity.</p>
 *
 * <p>Outbox events are produced for every lifecycle transition:
 * {@code LEAVE_REQUESTED}, {@code LEAVE_APPROVED}, {@code LEAVE_REJECTED},
 * {@code LEAVE_MODIFICATION_REQUESTED}, {@code LEAVE_CANCELLED}.</p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>{@code employee}         – the employee requesting leave</li>
 *   <li>{@code approver}         – the direct manager approving/rejecting</li>
 *   <li>{@code secondApprover}   – HR for leaves >5 consecutive days</li>
 *   <li>{@code executiveApprover} – Finance Manager+ for Unpaid leaves</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "pr_leave_requests", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "employee_id, start_date", name = "idx_lr_employee_date"),
        @Index(columnList = "approver_id, leave_request_status", name = "idx_lr_approver_status"),
        @Index(columnList = "legal_entity_id, leave_request_status", name = "idx_lr_entity_status")
    })
@Filter(name = "organizationFilter",
    condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "employee", "approver", "secondApprover", "executiveApprover"})
public class LeaveRequest extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Scope
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lr_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Employee
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_lr_employee"))
    @NotNull(message = "Employee is required")
    private Employee employee;

    // -------------------------------------------------------------------------
    // Leave Details
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_type", nullable = false, length = 20)
    @NotNull(message = "Leave type is required")
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    @NotNull(message = "Start date is required")
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    @NotNull(message = "End date is required")
    private LocalDate endDate;

    /** Decimal for half-days (e.g. 1.5). */
    @Column(name = "number_of_days", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal numberOfDays;

    /** Required for Sick/Unpaid, optional for Paid. Max 500 characters. */
    @Column(name = "reason", length = 500)
    @Size(max = 500)
    private String reason;

    /** MinIO path for medical certificate attachment (Sick leave). */
    @Column(name = "attachment_path", length = 500)
    @Size(max = 500)
    private String attachmentPath;

    // -------------------------------------------------------------------------
    // Balance Snapshot at Request Time
    // -------------------------------------------------------------------------

    @Column(name = "paid_balance_before", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal paidBalanceBefore;

    @Column(name = "sick_balance_before", nullable = false, precision = 5, scale = 1)
    @NotNull
    private BigDecimal sickBalanceBefore;

    @Column(name = "paid_days_used", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal paidDaysUsed;

    @Column(name = "sick_days_used", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal sickDaysUsed;

    /** Excess days that were converted to Unpaid. */
    @Column(name = "unpaid_days_used", nullable = false, precision = 4, scale = 1)
    @NotNull
    private BigDecimal unpaidDaysUsed;

    // -------------------------------------------------------------------------
    // Approval (LLR-PAY-01.3)
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approver_id",
        foreignKey = @ForeignKey(name = "fk_lr_approver"))
    private Employee approver;                  // Direct manager

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "second_approver_id",
        foreignKey = @ForeignKey(name = "fk_lr_second_approver"))
    private Employee secondApprover;            // HR (>5 consecutive days)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executive_approver_id",
        foreignKey = @ForeignKey(name = "fk_lr_executive_approver"))
    private Employee executiveApprover;         // Finance Manager+ (Unpaid leaves)

    @Enumerated(EnumType.STRING)
    @Column(name = "leave_request_status", nullable = false, length = 30)
    @Builder.Default
    private LeaveRequestStatus leaveRequestStatus = LeaveRequestStatus.PENDING;

    /** Rejection reason or modification suggestion. */
    @Column(name = "approver_comment", length = 500)
    @Size(max = 500)
    private String approverComment;

    // -------------------------------------------------------------------------
    // Lifecycle Dates
    // -------------------------------------------------------------------------

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
}
