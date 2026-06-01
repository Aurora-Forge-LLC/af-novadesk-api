package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.CapitalInjectionStatus;
import com.af.novadesk.api.finance.constants.FundingSource;
import com.af.novadesk.api.finance.constants.RateSource;
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
 * Represents a single capital-injection transaction (LLR-FIN-02).
 *
 * <p>A capital injection is the event in which an external or inter-entity
 * funding source transfers money into a {@link LegalEntity}'s cash / bank
 * account.  The record links the raw request to the ledger entries created
 * as a result and captures the exchange-rate information used for USD
 * reporting (LLR-FIN-02.3).</p>
 *
 * <p>For inter-entity transfers {@link FundingSource#INTER_ENTITY_TRANSFER},
 * a non-null {@code transferId} groups the four ledger legs across both
 * entity ledgers (LLR-FIN-02.4).</p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(name = "fa_capital_injections", schema = "af_novadesk")
@Filter(name = "organizationFilter",
        condition = "target_legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"targetEntity", "sourceEntity", "sourceAccount", "destinationAccount"})
public class CapitalInjection extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Entity References
    // -------------------------------------------------------------------------

    /** The entity receiving the capital (mandatory). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "target_legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ci_target_entity")
    )
    @NotNull(message = "Target entity is required")
    private LegalEntity targetEntity;

    /**
     * The entity providing the capital (only set for INTER_ENTITY_TRANSFER).
     * Null for external funding sources (FOUNDER_EQUITY, LOAN, GRANT).
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "source_legal_entity_id",
            foreignKey = @ForeignKey(name = "fk_ci_source_entity")
    )
    private LegalEntity sourceEntity;

    // -------------------------------------------------------------------------
    // Transfer Correlation
    // -------------------------------------------------------------------------

    /**
     * Shared UUID that correlates the four ledger legs of an inter-entity
     * transfer.  Null for standard (two-leg) injections.  LLR-FIN-02.4.
     */
    @Column(name = "transfer_id")
    private UUID transferId;

    // -------------------------------------------------------------------------
    // Funding Details
    // -------------------------------------------------------------------------

    @Enumerated(EnumType.STRING)
    @Column(name = "funding_source", nullable = false, length = 30)
    @NotNull(message = "Funding source is required")
    private FundingSource fundingSource;

    /** Date on which the funds were received / transferred.  Cannot be future. */
    @Column(name = "funding_date", nullable = false)
    @NotNull(message = "Funding date is required")
    private LocalDate fundingDate;

    // -------------------------------------------------------------------------
    // Amounts & Currency
    // -------------------------------------------------------------------------

    @Column(name = "amount_local", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Amount is required")
    @DecimalMin(value = "0.01", message = "Amount must be at least 0.01")
    private BigDecimal amountLocal;

    /**
     * ISO 4217 code of the local currency (derived from the target entity's
     * base currency at submission time).
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_local", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank(message = "Local currency is required")
    private String currencyLocal;

    /** Converted USD equivalent stored for cross-entity reporting. LLR-FIN-02.3. */
    @Column(name = "amount_usd", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal amountUsd;

    // -------------------------------------------------------------------------
    // Exchange Rate Audit (LLR-FIN-02.3)
    // -------------------------------------------------------------------------

    @Column(name = "exchange_rate_used", nullable = false, precision = 19, scale = 6)
    @NotNull
    private BigDecimal exchangeRateUsed;

    @Column(name = "rate_date_used", nullable = false)
    @NotNull
    private LocalDate rateDateUsed;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_source", nullable = false, length = 30)
    @NotNull
    private RateSource rateSource;

    /**
     * True when a lookback rate was used for USD conversion (LLR-FIN-04.4).
     * Flags the injection for review when the exact-date rate was unavailable.
     */
    @Column(name = "rate_warning", nullable = false)
    @Builder.Default
    private Boolean rateWarning = false;

    // -------------------------------------------------------------------------
    // Account References
    // -------------------------------------------------------------------------

    /** The account being credited (source of funds). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "source_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ci_source_account")
    )
    @NotNull(message = "Source account is required")
    private Account sourceAccount;

    /** The entity's cash / bank account being debited (destination). */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "destination_account_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_ci_destination_account")
    )
    @NotNull(message = "Destination account is required")
    private Account destinationAccount;

    // -------------------------------------------------------------------------
    // Optional Metadata
    // -------------------------------------------------------------------------

    @Column(name = "reference_number", length = 50)
    @Size(max = 50, message = "Reference number must not exceed 50 characters")
    private String referenceNumber;

    @Column(name = "notes", length = 500)
    @Size(max = 500, message = "Notes must not exceed 500 characters")
    private String notes;

    /** Audited identity of the user who submitted this injection. */
    @Column(name = "created_by", length = 100)
    @Size(max = 100)
    private String createdBy;

    // -------------------------------------------------------------------------
    // Lifecycle Status
    // -------------------------------------------------------------------------

    /** Current lifecycle status of this injection (LLR-FIN-02). */
    @Enumerated(EnumType.STRING)
    @Column(name = "injection_status", nullable = false, length = 20)
    @Builder.Default
    @NotNull
    private CapitalInjectionStatus injectionStatus = CapitalInjectionStatus.POSTED;
}


