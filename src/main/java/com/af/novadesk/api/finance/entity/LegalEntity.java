package com.af.novadesk.api.finance.entity;



import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import org.hibernate.annotations.Filter;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Represents an independent legal entity (e.g. a country-specific subsidiary)
 * whose financial records are maintained in isolation from other entities.
 *
 * <p>Implements LLR-FIN-01.1 (Entity Creation) and LLR-FIN-01.2 (Entity Activation).
 * The inherited {@code status} field (ACTIVE / INACTIVE) tracks the operational
 * state; {@link ApprovalStatus} tracks the Finance-team approval lifecycle.</p>
 *
 * <p>Relationships (all owned by this side):
 * <ul>
 *   <li>{@code fiscalYearSetting}  – 1:1, mandatory once approved</li>
 *   <li>{@code chartOfAccounts}    – 1:N, auto-generated on approval</li>
 *   <li>{@code bankAccounts}       – 1:N, auto-generated on approval</li>
 *   <li>{@code userAccesses}       – 1:N, grants users access to this entity</li>
 * </ul>
 * </p>
 */
import org.hibernate.annotations.Filter;

@Entity
@Table(
        name = "legal_entities",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "entity_name", name = "uk_legal_entity_name"),
                @UniqueConstraint(columnNames = "entity_code", name = "uk_legal_entity_code")
        }
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"chartOfAccounts", "bankAccounts", "userAccesses", "fiscalYearSetting"})
public class LegalEntity extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Identity
    // -------------------------------------------------------------------------

    /** Human-readable name; must be unique across all entities. LLR-FIN-01.1 */
    @Column(name = "entity_name", nullable = false, unique = true, length = 100)
    @NotBlank(message = "Entity name is required")
    @Size(max = 100, message = "Entity name must not exceed 100 characters")
    private String entityName;

    /**
     * Short alphanumeric code used as a prefix in document numbers.
     * 2–10 chars, uppercase enforced at the service layer. LLR-FIN-01.1
     */
    @Column(name = "entity_code", nullable = false, unique = true, length = 10)
    @NotBlank(message = "Entity code is required")
    @Size(min = 2, max = 10, message = "Entity code must be between 2 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Entity code must be alphanumeric uppercase; hyphens are allowed")
    private String entityCode;

    @Column(name = "organization_id")
    private UUID organizationId;

    // -------------------------------------------------------------------------
    // Geography & Currency
    // -------------------------------------------------------------------------

    /** ISO alpha-2 country; drives base currency and fiscal-year template. LLR-FIN-01.1 */
    @Column(name = "country", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Country is required")
    private CountryCode country;

    /**
     * ISO 4217 currency code derived from {@code country} on creation.
     * Stored explicitly so future multi-currency overrides are possible. LLR-FIN-01.1
     */
    @Column(name = "base_currency", nullable = false, length = 5)
    @NotBlank(message = "Base currency is required")
    private String baseCurrency;

    // -------------------------------------------------------------------------
    // Registration Details
    // -------------------------------------------------------------------------

    /** Government-issued tax or company registration number. Optional. LLR-FIN-01.1 */
    @Column(name = "tax_id", length = 50)
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;

    /** Legal date of incorporation. LLR-FIN-01.1 */
    @Column(name = "incorporation_date", nullable = false)
    @NotNull(message = "Incorporation date is required")
    private LocalDate incorporationDate;

    // -------------------------------------------------------------------------
    // Approval Lifecycle  (LLR-FIN-01.2)
    // -------------------------------------------------------------------------

    /**
     * Finance-team approval state. Starts as {@link ApprovalStatus#PENDING} on creation;
     * transitions to APPROVED or REJECTED after Finance review. LLR-FIN-01.2
     */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    // -------------------------------------------------------------------------
    // Owned Relationships
    // -------------------------------------------------------------------------

    /**
     * Country-specific fiscal year configuration, initialised on entity approval.
     * Cascade: persist/merge cascade so the setting is saved with the entity.
     */
    @OneToOne(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private FiscalYearSetting fiscalYearSetting;

    /** Auto-generated Chart of Accounts entries created on entity approval. LLR-FIN-01.2 */
    @Builder.Default
    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChartOfAccount> chartOfAccounts = new ArrayList<>();

    /** Default cash and operating bank account records created on approval. LLR-FIN-01.2 */
    @Builder.Default
    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EntityBankAccount> bankAccounts = new ArrayList<>();

    /** Access grants linking users to this entity. LLR-FIN-01.3 */
    @Builder.Default
    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EntityUserAccess> userAccesses = new ArrayList<>();
}