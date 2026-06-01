package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.ExpenseTransactionStatus;
import com.af.novadesk.api.finance.constants.PaymentMethod;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Records a single manually-entered expense against a {@link LegalEntity} (LLR-FIN-03).
 *
 * <p>Every saved expense produces exactly two {@link LedgerEntry}
 * rows in the same transaction — one CREDIT on the source account (funds leaving) and one DEBIT
 * on the destination account (expense recognised). The system validates that
 * {@code SUM(debits) == SUM(credits)} before persisting (LLR-FIN-03.2).</p>
 *
 * <p>Currency is stored explicitly at the transaction level — taken from the entity's
 * {@code baseCurrency} at submission time — so that historical records remain accurate
 * even if the entity's currency is changed in the future.</p>
 *
 * <p>The inherited {@code status} field (ACTIVE / INACTIVE) handles soft-delete.
 * {@link ExpenseTransactionStatus} tracks the accounting lifecycle (POSTED / VOID).</p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>{@code legalEntity}          – the entity this expense is recorded against</li>
 *   <li>{@code vendor}               – the payee</li>
 *   <li>{@code createdBy}            – shadow user who submitted the expense</li>
 *   <li>{@code sourceAccount}        – account being credited (funds leave here)</li>
 *   <li>{@code destinationAccount}   – account being debited (expense recognised here)</li>
 *   <li>{@code attachments}          – uploaded invoices / receipts (LLR-FIN-03.4)</li>
 * </ul>
 * </p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(
        name = "exp_expense_transactions",
        schema = "af_novadesk",
        indexes = {
                // Most common query: all expenses for an entity ordered by date
                @Index(columnList = "legal_entity_id, expense_date", name = "idx_exp_txn_entity_date"),
                // Vendor drill-down
                @Index(columnList = "vendor_id",                     name = "idx_exp_txn_vendor_id"),
                // Auditor: all expenses created by a specific user
                @Index(columnList = "created_by_shadow_user_id",     name = "idx_exp_txn_created_by")
        }
)
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "vendor", "createdBy", "sourceAccount", "destinationAccount", "attachments"})
public class ExpenseTransaction extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Entity & Org Scope
    // -------------------------------------------------------------------------

    /**
     * The legal entity whose ledger this expense is posted to.
     * All accounts, currency, and org context are derived from this entity.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_et_legal_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Vendor
    // -------------------------------------------------------------------------

    /**
     * The vendor (payee) this expense was paid to.
     * Resolved via autocomplete on the expense form (LLR-FIN-03.1).
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "vendor_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_et_vendor")
    )
    @NotNull(message = "Vendor is required")
    private Vendor vendor;

    // -------------------------------------------------------------------------
    // Audit — Who Recorded This Expense
    // -------------------------------------------------------------------------

    /**
     * The finance operator who submitted this expense.
     * Resolved from the JWT {@code sub} claim at submission time.
     * Stored as a ShadowUser FK to enable joins for audit reports without
     * calling AuthHub.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "created_by_shadow_user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_et_created_by")
    )
    @NotNull(message = "Created by is required")
    private ShadowUser createdBy;

    // -------------------------------------------------------------------------
    // Expense Details  (LLR-FIN-03.1)
    // -------------------------------------------------------------------------

    /** The date the expense was incurred. Cannot be a future date. */
    @Column(name = "expense_date", nullable = false)
    @NotNull(message = "Expense date is required")
    private LocalDate expenseDate;

    /**
     * The transaction amount in the entity's base currency.
     * Must be greater than zero. LLR-FIN-03.1.
     */
    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amount;

    /**
     * ISO 4217 currency code. Derived from the legal entity's {@code baseCurrency}
     * at submission time and stored explicitly for historical accuracy. LLR-FIN-03.1.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currencyCode;

    /**
     * How the expense was settled. Drives reporting and bank reconciliation. LLR-FIN-03.1.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    @NotNull(message = "Payment method is required")
    private PaymentMethod paymentMethod;

    // -------------------------------------------------------------------------
    // Double-Entry Account References  (LLR-FIN-03.2)
    // -------------------------------------------------------------------------

    /**
     * The account being CREDITED — funds leave this account (e.g. Company Bank Account).
     * Must differ from {@link #destinationAccount}. LLR-FIN-03.2.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_et_source_account")
    )
    @NotNull(message = "Source account is required")
    private Account sourceAccount;

    /**
     * The account being DEBITED — the expense is recognised here (e.g. Cloud Infrastructure Expense).
     * Must differ from {@link #sourceAccount}. LLR-FIN-03.2.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "destination_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_et_destination_account")
    )
    @NotNull(message = "Destination account is required")
    private Account destinationAccount;

    // -------------------------------------------------------------------------
    // Reference & Narrative
    // -------------------------------------------------------------------------

    /**
     * Vendor-issued invoice or receipt number. Optional.
     * Used for reconciliation against vendor statements. LLR-FIN-03.1.
     */
    @Column(name = "invoice_receipt_number", length = 50)
    @Size(max = 50, message = "Invoice/receipt number must not exceed 50 characters")
    private String invoiceReceiptNumber;

    /**
     * Human-readable description of what the expense was for. Mandatory.
     * Appears in the general ledger and audit trail. LLR-FIN-03.1.
     */
    @Column(name = "description", nullable = false, length = 500)
    @NotBlank(message = "Description is required")
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    // -------------------------------------------------------------------------
    // Manual Exchange Rate (audit trail — LLR-FIN-03.3)
    // -------------------------------------------------------------------------

    /**
     * The manual exchange rate supplied by the caller when the system could not
     * resolve one automatically (HTTP 422 / FIN_RATE_002 path).
     * Null when the system resolved the rate automatically.
     */
    @Column(name = "manual_exchange_rate", precision = 19, scale = 6)
    private BigDecimal manualExchangeRate;

    /**
     * Mandatory audit justification for why a manual rate was used.
     * Present only when {@link #manualExchangeRate} is non-null.
     */
    @Column(name = "manual_rate_justification", length = 500)
    @Size(max = 500)
    private String manualRateJustification;

    /**
     * Name or email of the person who approved the manual rate.
     * Present only when {@link #manualExchangeRate} is non-null.
     */
    @Column(name = "manual_rate_approved_by", length = 150)
    @Size(max = 150)
    private String manualRateApprovedBy;

    // -------------------------------------------------------------------------
    // Accounting Lifecycle
    // -------------------------------------------------------------------------

    /**
     * Accounting state of this transaction. Starts as {@link ExpenseTransactionStatus#POSTED}
     * after a successful save. Transitions to {@link ExpenseTransactionStatus#VOID}
     * when the transaction is cancelled and reversed.
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_status", nullable = false, length = 20)
    @NotNull(message = "Transaction status is required")
    private ExpenseTransactionStatus transactionStatus = ExpenseTransactionStatus.POSTED;

    // -------------------------------------------------------------------------
    // Attachments  (LLR-FIN-03.4)
    // -------------------------------------------------------------------------

    /**
     * Uploaded invoices, receipts, or supporting documents for this expense.
     * Multiple attachments are allowed per transaction.
     * Cascade: all lifecycle operations propagate to attachments.
     */
    @Builder.Default
    @OneToMany(mappedBy = "expenseTransaction", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ExpenseAttachment> attachments = new ArrayList<>();
}
