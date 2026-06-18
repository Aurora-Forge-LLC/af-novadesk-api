package com.af.novadesk.api.common.entity;

import com.af.novadesk.api.common.constants.LedgerEntrySide;
import com.af.novadesk.api.common.constants.LedgerModule;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Unified double-entry ledger line shared by finance (expense, capital injection,
 * bank categorization) and payroll modules.
 *
 * <p>Account data is stored as string snapshots (accountCode / accountName) at
 * posting time — no live FK to Account or ChartOfAccount — so the ledger remains
 * accurate even after account renames or deletions.</p>
 *
 * <p>FX fields (amountUsd, exchangeRateUsed, rateDateUsed, rateWarning) are
 * nullable to accommodate payroll entries whose local currency equals USD.</p>
 */
@Entity
@Table(
    name = "ledger_entries",
    schema = "af_novadesk",
    indexes = {
        @Index(name = "idx_le_journal_id",       columnList = "journal_id"),
        @Index(name = "idx_le_entity_date",      columnList = "legal_entity_id, created_at"),
        @Index(name = "idx_le_reference",        columnList = "reference_type, reference_id"),
        @Index(name = "idx_le_module",           columnList = "module"),
        @Index(name = "idx_le_account_code",     columnList = "legal_entity_id, account_code"),
        @Index(name = "idx_le_transfer_id",      columnList = "transfer_id")
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
@ToString(exclude = "legalEntity")
public class LedgerEntry extends AbstractEntity {

    // ─── Journal Correlation ──────────────────────────────────────────────────

    @Column(name = "journal_id", nullable = false)
    @NotNull
    private UUID journalId;

    /** Links the four legs of an inter-entity transfer. Null for standard entries. */
    @Column(name = "transfer_id")
    private UUID transferId;

    // ─── Entity Scope ─────────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
                foreignKey = @ForeignKey(name = "fk_le_legal_entity"))
    @NotNull
    private LegalEntity legalEntity;

    // ─── Module Discriminator ─────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "module", nullable = false, length = 30)
    @NotNull
    private LedgerModule module;

    // ─── Account Snapshot ─────────────────────────────────────────────────────

    @Column(name = "account_code", nullable = false, length = 50)
    @NotBlank
    private String accountCode;

    @Column(name = "account_name", nullable = false, length = 200)
    @NotBlank
    private String accountName;

    // ─── Entry Side ───────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_side", nullable = false, length = 10)
    @NotNull
    private LedgerEntrySide entrySide;

    // ─── Amounts & Currency ───────────────────────────────────────────────────

    @Column(name = "amount_local", nullable = false, precision = 19, scale = 4)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amountLocal;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_local", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank
    private String currencyLocal;

    /** Nullable — payroll entries that are already in USD omit this. */
    @Column(name = "amount_usd", precision = 19, scale = 4)
    private BigDecimal amountUsd;

    @Column(name = "exchange_rate_used", precision = 19, scale = 6)
    private BigDecimal exchangeRateUsed;

    @Column(name = "rate_date_used")
    private LocalDate rateDateUsed;

    @Column(name = "rate_warning")
    @Builder.Default
    private Boolean rateWarning = false;

    // ─── Reversal Support ─────────────────────────────────────────────────────

    @Column(name = "is_reversal", nullable = false)
    @Builder.Default
    private Boolean isReversal = false;

    /** Non-null only for reversal entries; links back to the original entry. */
    @Column(name = "original_entry_id")
    private UUID originalEntryId;

    // ─── Narrative & Reference ────────────────────────────────────────────────

    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;

    @Column(name = "reference_type", nullable = false, length = 50)
    @NotBlank
    private String referenceType;

    @Column(name = "reference_id", nullable = false)
    @NotNull
    private UUID referenceId;
}
