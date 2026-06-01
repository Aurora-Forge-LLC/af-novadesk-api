package com.af.novadesk.api.finance.entity;



import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.constants.AccountType;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * A single account entry within a {@link LegalEntity}'s Chart of Accounts.
 *
 * <p>Separated from {@code LegalEntity} to satisfy 3NF: each account row has
 * attributes (code, name, type, description) that are functionally dependent
 * only on the account's own PK, not on other accounts of the same entity.</p>
 *
 * <p>Implements LLR-FIN-01.2: "Generate a default Chart of Accounts based on
 * country template." A service layer populates these rows from a per-country
 * template when an entity is approved.</p>
 *
 * <p>The composite unique constraint {@code (legal_entity_id, account_code)}
 * ensures account codes are unique within an entity but may repeat across entities.</p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(
        name = "chart_of_accounts",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"legal_entity_id", "account_code"},
                        name = "uk_coa_entity_account_code"
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
@ToString(exclude = {"legalEntity"})
public class ChartOfAccount extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Parent Reference
    // -------------------------------------------------------------------------

    /** The entity that owns this account. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_coa_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Account Identity
    // -------------------------------------------------------------------------

    /**
     * Numeric or alphanumeric account code following the entity's CoA numbering scheme.
     * Unique within the entity. E.g. "1000" for Cash, "2000" for Accounts Payable.
     */
    @Column(name = "account_code", nullable = false, length = 20)
    @NotBlank(message = "Account code is required")
    @Size(max = 20, message = "Account code must not exceed 20 characters")
    private String accountCode;

    /** Human-readable account name, e.g. "Cash and Cash Equivalents". */
    @Column(name = "account_name", nullable = false, length = 150)
    @NotBlank(message = "Account name is required")
    @Size(max = 150, message = "Account name must not exceed 150 characters")
    private String accountName;

    /** Double-entry category this account belongs to. */
    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 20)
    @NotNull(message = "Account type is required")
    private AccountType accountType;

    /** Optional plain-text description of the account's purpose. */
    @Column(name = "description", length = 500)
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    // -------------------------------------------------------------------------
    // Hierarchy Support
    // -------------------------------------------------------------------------

    /**
     * Self-referential parent account for hierarchical CoA structures
     * (e.g. "Current Assets" → "Cash" → "Petty Cash").
     * Null indicates a top-level account.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_account_id",
            foreignKey = @ForeignKey(name = "fk_coa_parent_account"))
    private ChartOfAccount parentAccount;

    // -------------------------------------------------------------------------
    // Flags
    // -------------------------------------------------------------------------

    /**
     * When {@code true}, journal entries can be posted directly to this account.
     * Header/summary accounts set this to {@code false}.
     */
    @Builder.Default
    @Column(name = "is_postable", nullable = false)
    private boolean postable = true;

    /**
     * Indicates this account was auto-generated from the country template.
     * Helps distinguish template defaults from manually added accounts.
     */
    @Builder.Default
    @Column(name = "is_system_generated", nullable = false)
    private boolean systemGenerated = false;
}