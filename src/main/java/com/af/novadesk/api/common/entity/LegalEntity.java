package com.af.novadesk.api.common.entity;

import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
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
 * <p>Moved to {@code common} module — shared across finance, payroll, and asset modules.
 * Note: relationships to finance-specific entities (ChartOfAccount, EntityBankAccount,
 * EntityUserAccess) are retained for now and will be decoupled in a later refactoring phase.</p>
 *
 * <p>Implements LLR-FIN-01.1 (Entity Creation) and LLR-FIN-01.2 (Entity Activation).</p>
 */
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

    @Column(name = "entity_name", nullable = false, unique = true, length = 100)
    @NotBlank(message = "Entity name is required")
    @Size(max = 100, message = "Entity name must not exceed 100 characters")
    private String entityName;

    @Column(name = "entity_code", nullable = false, unique = true, length = 10)
    @NotBlank(message = "Entity code is required")
    @Size(min = 2, max = 10, message = "Entity code must be between 2 and 10 characters")
    @Pattern(regexp = "^[A-Z0-9-]+$", message = "Entity code must be alphanumeric uppercase; hyphens are allowed")
    private String entityCode;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "country", nullable = false, length = 5)
    @Enumerated(EnumType.STRING)
    @NotNull(message = "Country is required")
    private CountryCode country;

    @Column(name = "base_currency", nullable = false, length = 5)
    @NotBlank(message = "Base currency is required")
    private String baseCurrency;

    @Column(name = "tax_id", length = 50)
    @Size(max = 50, message = "Tax ID must not exceed 50 characters")
    private String taxId;

    @Column(name = "incorporation_date", nullable = false)
    @NotNull(message = "Incorporation date is required")
    private LocalDate incorporationDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 20)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    @OneToOne(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private FiscalYearSetting fiscalYearSetting;

    @Builder.Default
    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ChartOfAccount> chartOfAccounts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EntityBankAccount> bankAccounts = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "legalEntity", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<EntityUserAccess> userAccesses = new ArrayList<>();
}
