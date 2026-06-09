package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.finance.constants.AccountRole;
import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A financial account belonging to a {@link LegalEntity} within the capital-funding
 * sub-domain.  Maps to the {@code fa_accounts} table.
 *
 * <p>Each entity has a set of ACTIVE accounts, one per {@link AccountRole}, that
 * are used as debit / credit targets during {@link CapitalInjection} posting
 * (LLR-FIN-02.2).  Accounts are typically seeded by an entity-activation process;
 * ad-hoc accounts may be added by Finance administrators.</p>
 *
 * <p>Currency is stored explicitly for direct querying without joining to
 * {@link LegalEntity}, but must always match {@code legalEntity.baseCurrency}.</p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(
        name = "fa_accounts",
        schema = "af_novadesk",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"legal_entity_id", "account_code"},
                        name = "uq_fa_accounts_entity_code"
                )
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
public class Account extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Parent Reference
    // -------------------------------------------------------------------------

    /**
     * The legal entity that owns this account.
     * Provides currency, entity code, and approval status in a single join.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "legal_entity_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_fa_account_legal_entity")
    )
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Account Identity
    // -------------------------------------------------------------------------

    /**
     * Alphanumeric account code, unique within the entity.
     * E.g. "1000" (Cash), "3100" (Founder Equity).
     */
    @Column(name = "account_code", nullable = false, length = 30)
    @NotBlank(message = "Account code is required")
    @Size(max = 30, message = "Account code must not exceed 30 characters")
    private String accountCode;

    /** Human-readable label, e.g. "Bank - Operating Account (USD)". */
    @Column(name = "account_name", nullable = false, length = 150)
    @NotBlank(message = "Account name is required")
    @Size(max = 150, message = "Account name must not exceed 150 characters")
    private String accountName;

    // -------------------------------------------------------------------------
    // Classification
    // -------------------------------------------------------------------------

    /**
     * The functional role this account plays during automated journal posting.
     * Drives account resolution in {@code CapitalInjectionService}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_role", nullable = false, length = 50)
    @NotNull(message = "Account role is required")
    private AccountRole accountRole;

    /**
     * Standard double-entry type (ASSET, LIABILITY, EQUITY, REVENUE, EXPENSE).
     * Uses the shared {@link AccountType} from {@code finance.constants}.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 30)
    @NotNull(message = "Account type is required")
    private AccountType accountType;

    // -------------------------------------------------------------------------
    // Currency
    // -------------------------------------------------------------------------

    /**
     * ISO 4217 currency code (e.g. "USD", "INR").
     * Denormalised from {@code legalEntity.baseCurrency} for query efficiency.
     */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency_code", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank(message = "Currency code is required")
    @Size(min = 3, max = 3, message = "Currency code must be exactly 3 characters")
    private String currencyCode;
}

