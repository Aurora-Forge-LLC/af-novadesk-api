package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.finance.constants.MatchingMethod;
import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Represents a single transaction extracted from a bank statement (LLR-BNK-01.4).
 *
 * <p>Each transaction belongs to a {@link BankStatement}, references the
 * {@link LegalEntity} and {@link EntityBankAccount}, and tracks its
 * reconciliation state via {@link ReconciliationStatus}.</p>
 *
 * <p>Org-scoping chains through {@code legal_entity_id → legal_entities.organization_id}
 * via the {@code organizationFilter} Hibernate filter.</p>
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

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "statement_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_txn_statement")
    )
    @NotNull(message = "Statement is required")
    private BankStatement statement;

    // -------------------------------------------------------------------------
    // Entity & Bank Account references (denormalised for query performance)
    // -------------------------------------------------------------------------

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_bnk_txn_legal_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

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

    @Column(name = "transaction_date", nullable = false)
    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    @Column(name = "description", nullable = false, length = 500)
    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Amount is required")
    private BigDecimal amount;  // positive = credit (money in), negative = debit (money out)

    @Column(name = "balance", precision = 19, scale = 4)
    private BigDecimal balance;  // running balance from statement

    // -------------------------------------------------------------------------
    // Reconciliation State
    // -------------------------------------------------------------------------

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "reconciliation_status", nullable = false, length = 30)
    private ReconciliationStatus reconciliationStatus = ReconciliationStatus.UNMATCHED;

    @Column(name = "matched_expense_id")
    private UUID matchedExpenseId;  // FK to exp_expense_transactions (added after matching)

    @Column(name = "matching_score")
    private Integer matchingScore;  // 0-100

    @Enumerated(EnumType.STRING)
    @Column(name = "matching_method", length = 30)
    private MatchingMethod matchingMethod;

    // -------------------------------------------------------------------------
    // Optimistic Locking
    // -------------------------------------------------------------------------

    @Version
    @Column(name = "version")
    private Long version;
}
