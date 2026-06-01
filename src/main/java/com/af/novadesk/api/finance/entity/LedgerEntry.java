package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.LedgerEntrySide;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.entity.LegalEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An immutable double-entry ledger line posted against a specific {@link Account}
 * for a given {@link LegalEntity} (LLR-FIN-02.2).
 *
 * <p>Each capital-injection workflow produces exactly two ledger entries for a
 * standard injection and four entries for an inter-entity transfer.  All entries
 * in the same journal batch share a common {@code journalId}.  Inter-entity entries
 * additionally share a non-null {@code transferId} for reconciliation (LLR-FIN-02.4).</p>
 *
 * <p>Both local-currency and USD amounts are stored to support multi-currency
 * reporting without re-computation (LLR-FIN-02.3).</p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "fa_ledger_entries", schema = "af_novadesk")
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "account"})
public class LedgerEntry extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Journal Correlation
    // -------------------------------------------------------------------------

    /** Groups all legs posted in the same journal batch. */
    @Column(name = "journal_id", nullable = false)
    @NotNull(message = "Journal ID is required")
    private UUID journalId;

    /**
     * Links the four legs of an inter-entity transfer across both entity ledgers.
     * Null for standard (two-leg) injections (LLR-FIN-02.4).
     */
    @Column(name = "transfer_id")
    private UUID transferId;

    // -------------------------------------------------------------------------
    // Entity & Account
    // -------------------------------------------------------------------------

    /** Entity whose ledger this entry belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_le_legal_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    /** The account being debited or credited. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_le_account")
    )
    @NotNull(message = "Account is required")
    private Account account;

    // -------------------------------------------------------------------------
    // Entry Side
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_side", nullable = false, length = 10)
    @NotNull(message = "Entry side is required")
    private LedgerEntrySide entrySide;

    // -------------------------------------------------------------------------
    // Amounts & Currency
    // -------------------------------------------------------------------------

    @Column(name = "amount_local", nullable = false, precision = 19, scale = 4)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amountLocal;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_local", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank
    private String currencyLocal;

    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 4)
    @NotNull
    @DecimalMin(value = "0.01")
    private BigDecimal amountUsd;

    @Column(name = "exchange_rate_used", nullable = false, precision = 19, scale = 6)
    @NotNull
    private BigDecimal exchangeRateUsed;

    @Column(name = "rate_date_used", nullable = false)
    @NotNull
    private LocalDate rateDateUsed;

    /**
     * True when a lookback rate was used for USD conversion (LLR-FIN-04.4).
     * Flags the entry for review when the exact-date rate was unavailable
     * and the nearest past rate within the look-back window was used instead.
     */
    @Column(name = "rate_warning", nullable = false)
    @Builder.Default
    private Boolean rateWarning = false;

    // -------------------------------------------------------------------------
    // Narrative & Reference
    // -------------------------------------------------------------------------

    @Column(name = "description", length = 500)
    @Size(max = 500)
    private String description;

    /** The type of the originating document, e.g. "CAPITAL_INJECTION". */
    @Column(name = "reference_type", nullable = false, length = 50)
    @NotBlank
    private String referenceType;

    /** PK of the originating document (e.g. CapitalInjection.id). */
    @Column(name = "reference_id", nullable = false)
    @NotNull
    private UUID referenceId;
}

