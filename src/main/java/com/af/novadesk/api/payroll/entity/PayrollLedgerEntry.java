package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payroll-specific immutable double-entry ledger entry (LLR-PAY-03).
 *
 * <p>Created when a payroll batch is approved. This is a payroll-owned ledger
 * table — NOT shared with the finance module's {@link com.af.novadesk.api.finance.entity.LedgerEntry}.
 * Each payroll batch produces balanced debit/credit entries aggregated by entity.</p>
 *
 * <p>The {@code referenceType = "PAYROLL"} field anticipates future consolidation
 * into a common ledger module.</p>
 *
 * <p>For voided batches, reversing entries are created with {@code isReversal = true}
 * and {@code originalEntryId} linking back to the original entry (LLR-PAY-03.5).</p>
 */
@Entity
@Table(name = "pr_payroll_ledger_entries", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "journal_id, entry_side", name = "idx_ple_journal_side"),
        @Index(columnList = "legal_entity_id, created_at", name = "idx_ple_entity_date"),
        @Index(columnList = "payroll_batch_id", name = "idx_ple_batch_id")
    })
@Filter(name = "organizationFilter",
    condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "payrollBatch"})
public class PayrollLedgerEntry extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Journal Correlation
    // -------------------------------------------------------------------------

    /** Groups all entries in the same journal batch. */
    @Column(name = "journal_id", nullable = false)
    @NotNull
    private UUID journalId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payroll_batch_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_ple_batch"))
    @NotNull(message = "Payroll batch is required")
    private PayrollBatch payrollBatch;

    // -------------------------------------------------------------------------
    // Entity Scope
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_ple_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Account Reference
    // -------------------------------------------------------------------------

    @Column(name = "account_code", nullable = false, length = 50)
    @NotBlank
    private String accountCode;                 // e.g. "SALARY_EXPENSE", "EMPLOYEE_PAYABLE"

    @Column(name = "account_description", nullable = false, length = 200)
    @NotBlank
    private String accountDescription;

    // -------------------------------------------------------------------------
    // Entry Side
    // -------------------------------------------------------------------------

    @Column(name = "entry_side", nullable = false, length = 10)
    @NotBlank
    private String entrySide;                   // DEBIT or CREDIT

    // -------------------------------------------------------------------------
    // Amounts
    // -------------------------------------------------------------------------

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal amount;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank
    private String currencyCode;

    @Column(name = "amount_usd", precision = 19, scale = 4)
    private BigDecimal amountUsd;               // USD equivalent (multi-currency entities)

    @Column(name = "exchange_rate_used", precision = 19, scale = 6)
    private BigDecimal exchangeRateUsed;

    // -------------------------------------------------------------------------
    // Reversal Support (LLR-PAY-03.5)
    // -------------------------------------------------------------------------

    @Column(name = "is_reversal")
    @Builder.Default
    private Boolean isReversal = false;

    @Column(name = "original_entry_id")
    private UUID originalEntryId;               // Links reversal to original entry

    // -------------------------------------------------------------------------
    // Narrative & Reference
    // -------------------------------------------------------------------------

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "reference_type", nullable = false, length = 50)
    @Builder.Default
    @NotBlank
    private String referenceType = "PAYROLL";

    @Column(name = "reference_id", nullable = false)
    @NotNull
    private UUID referenceId;                   // PK of the PayrollBatch
}
