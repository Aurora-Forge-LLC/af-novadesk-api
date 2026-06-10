package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.constants.MatchingMethod;
import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Represents a single transaction row extracted from a bank statement (LLR-BNK-01).
 *
 * <p>Each transaction belongs to exactly one {@link BankStatement} and inherits
 * its entity/bank-account scope. The {@link #amount} field uses a signed convention:
 * positive = credit (money in), negative = debit (money out).</p>
 *
 * <p>Reconciliation lifecycle: UNMATCHED → SUGGESTED → MATCHED, or IGNORED.</p>
 */
@Entity
@Table(
        name = "bnk_transactions",
        schema = "af_novadesk"
)
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"statement", "legalEntity", "bankAccount"})
public class BankTransaction extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Parent Statement
    // -------------------------------------------------------------------------

    /** The bank statement this transaction was extracted from. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "statement_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_txn_statement")
    )
    @NotNull(message = "Statement is required")
    private BankStatement statement;

    // -------------------------------------------------------------------------
    // Denormalized Scope (for query performance)
    // -------------------------------------------------------------------------

    /** Denormalized — the entity this transaction belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_txn_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    /** Denormalized — the bank account this transaction belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "bank_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_txn_bank_account")
    )
    @NotNull(message = "Bank account is required")
    private EntityBankAccount bankAccount;

    // -------------------------------------------------------------------------
    // Transaction Data
    // -------------------------------------------------------------------------

    /** The date of the transaction as recorded on the bank statement. */
    @Column(name = "transaction_date", nullable = false)
    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    /** Narration/description from the bank statement. */
    @Column(name = "description", nullable = false, length = 500)
    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    /**
     * Transaction amount. Positive = credit (money in), Negative = debit (money out).
     * Stored with 4 decimal places for precision.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Amount is required")
    private BigDecimal amount;

    /** Running balance after this transaction (nullable — not all banks provide this). */
    @Column(name = "balance", precision = 19, scale = 4)
    private BigDecimal balance;

    // -------------------------------------------------------------------------
    // Reconciliation State
    // -------------------------------------------------------------------------

    /** Current reconciliation status. Default: UNMATCHED. */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNMATCHED;

    /** FK to fa_ledger_entries — set after a successful match. Nullable. */
    @Column(name = "matched_ledger_entry_id")
    private java.util.UUID matchedLedgerEntryId;

    /** Matching score (0-100) from the matching algorithm. Nullable until matched. */
    @Column(name = "matching_score")
    private Integer matchingScore;

    /** How the match was established. Nullable until matched. */
    @Enumerated(EnumType.STRING)
    @Column(name = "matching_method", length = 30)
    private MatchingMethod matchingMethod;
}
