package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.payroll.constants.PayrollBatchStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the payroll generation workflow (LLR-PAY-02, PAY-03).
 *
 * <p>Represents a single payroll run for a {@link LegalEntity} within a pay
 * period. Contains:
 * <ul>
 *   <li>{@link Payslip} children — one per employee</li>
 *   <li>{@link PayrollFlaggedEmployee} children — employees requiring review</li>
 *   <li>{@link PayrollLedgerEntry} children — double-entry ledger rows</li>
 * </ul>
 * </p>
 *
 * <p>Outbox events: {@code PAYROLL_INITIATED}, {@code PAYROLL_APPROVED}
 * (→ ledger sync), {@code PAYROLL_REJECTED}, {@code PAYROLL_VOIDED}.</p>
 */
@Entity
@Table(name = "pr_payroll_batches", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "legal_entity_id, pay_period_start", name = "idx_pb_entity_period"),
        @Index(columnList = "batch_status, created_at", name = "idx_pb_status_created")
    })
@Filter(name = "organizationFilter",
    condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "approvedBy", "payslips", "flaggedEmployees", "ledgerEntries"})
public class PayrollBatch extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_pb_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Period
    // -------------------------------------------------------------------------

    @Column(name = "pay_period_start", nullable = false)
    @NotNull
    private LocalDate payPeriodStart;

    @Column(name = "pay_period_end", nullable = false)
    @NotNull
    private LocalDate payPeriodEnd;

    @Column(name = "payment_date", nullable = false)
    @NotNull
    private LocalDate paymentDate;

    // -------------------------------------------------------------------------
    // Summary Counts
    // -------------------------------------------------------------------------

    @Column(name = "total_headcount", nullable = false)
    @NotNull
    private Integer totalHeadcount;

    @Column(name = "processed_count", nullable = false)
    @NotNull
    private Integer processedCount;

    @Column(name = "flagged_count", nullable = false)
    @Builder.Default
    private Integer flaggedCount = 0;

    // -------------------------------------------------------------------------
    // Financial Summary
    // -------------------------------------------------------------------------

    @Column(name = "total_gross_salary", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    @NotNull
    private BigDecimal totalGrossSalary = BigDecimal.ZERO;

    @Column(name = "total_deductions", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    @NotNull
    private BigDecimal totalDeductions = BigDecimal.ZERO;

    @Column(name = "total_net_payout", nullable = false, precision = 19, scale = 4)
    @Builder.Default
    @NotNull
    private BigDecimal totalNetPayout = BigDecimal.ZERO;

    // -------------------------------------------------------------------------
    // Currency
    // -------------------------------------------------------------------------

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotNull
    private String currencyCode;

    @Column(name = "exchange_rate_usd", precision = 19, scale = 6)
    private BigDecimal exchangeRateUsd;

    @Column(name = "total_net_payout_usd", precision = 19, scale = 4)
    private BigDecimal totalNetPayoutUsd;

    // -------------------------------------------------------------------------
    // Status
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "batch_status", nullable = false, length = 30)
    @Builder.Default
    @NotNull
    private PayrollBatchStatus batchStatus = PayrollBatchStatus.INITIATED;

    // -------------------------------------------------------------------------
    // Approval
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by",
        foreignKey = @ForeignKey(name = "fk_pb_approved_by"))
    private Employee approvedBy;                // Finance Manager

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "rejection_reason", length = 500)
    private String rejectionReason;

    // -------------------------------------------------------------------------
    // Ledger Sync (PAY-03)
    // -------------------------------------------------------------------------

    @Column(name = "journal_id")
    private UUID journalId;                     // Groups all PayrollLedgerEntry rows

    @Column(name = "ledger_posted_at")
    private LocalDateTime ledgerPostedAt;

    // -------------------------------------------------------------------------
    // Children
    // -------------------------------------------------------------------------

    @Builder.Default
    @OneToMany(mappedBy = "payrollBatch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<Payslip> payslips = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "payrollBatch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayrollFlaggedEmployee> flaggedEmployees = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "payrollBatch", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<PayrollLedgerEntry> ledgerEntries = new ArrayList<>();
}
