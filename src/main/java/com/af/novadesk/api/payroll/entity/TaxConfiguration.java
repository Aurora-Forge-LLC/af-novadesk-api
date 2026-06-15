package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.payroll.constants.Jurisdiction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-entity tax configuration for a specific {@link Jurisdiction}
 * (LLR-PAY-04.6). Tax slabs and rates are stored in the database so that
 * admins can update them via the UI without code changes.
 *
 * <p>New jurisdictions can be added by creating a new
 * {@link Jurisdiction} enum value and implementing the corresponding
 * {@code TaxCalculationStrategy} — no schema changes needed.</p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>{@code legalEntity} – the entity this configuration applies to</li>
 *   <li>{@code taxSlabs}    – progressive tax rate slabs</li>
 *   <li>{@code lastModifiedBy} – employee who last updated this config</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "pr_tax_configurations", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"legal_entity_id", "jurisdiction", "tax_type"},
            name = "uk_tc_entity_jurisdiction_type")
    })
@Filter(name = "organizationFilter",
    condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "taxSlabs", "lastModifiedBy"})
public class TaxConfiguration extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_tc_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    @Column(name = "tax_name", length = 100)
    private String taxName;                       // Human-readable label, e.g. "Income Tax 2026"

    @Column(name = "tax_type", nullable = false, length = 50)
    private String taxType = "GENERAL";           // Discriminator for multiple configs per entity+jurisdiction

    @Enumerated(EnumType.STRING)
    @Column(name = "jurisdiction", nullable = false, length = 10)
    @NotNull
    private Jurisdiction jurisdiction;

    // -------------------------------------------------------------------------
    // SSF (Nepal) / PF (India) Rates
    // -------------------------------------------------------------------------

    @Column(name = "ssf_employee_rate", precision = 5, scale = 4)
    private BigDecimal ssfEmployeeRate;         // 0.11 for Nepal (11%)

    @Column(name = "ssf_employer_rate", precision = 5, scale = 4)
    private BigDecimal ssfEmployerRate;         // 0.20 for Nepal (20%)

    @Column(name = "ssf_max_cap_amount", precision = 19, scale = 4)
    private BigDecimal ssfMaxCapAmount;         // NPR 50,000 max gross for SSF

    @Column(name = "pf_employee_rate", precision = 5, scale = 4)
    private BigDecimal pfEmployeeRate;          // 0.12 for India (12%)

    @Column(name = "pf_employer_rate", precision = 5, scale = 4)
    private BigDecimal pfEmployerRate;          // 0.12 for India (12%)

    @Column(name = "pf_max_cap_amount", precision = 19, scale = 4)
    private BigDecimal pfMaxCapAmount;          // INR 15,000 for India PF cap

    // -------------------------------------------------------------------------
    // Professional Tax (India)
    // -------------------------------------------------------------------------

    @Column(name = "professional_tax_amount", precision = 19, scale = 4)
    private BigDecimal professionalTaxAmount;

    @Column(name = "professional_tax_state", length = 50)
    private String professionalTaxState;

    // -------------------------------------------------------------------------
    // Metadata
    // -------------------------------------------------------------------------

    @Column(name = "effective_from", nullable = false)
    @NotNull
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;              // Null = currently active

    @Column(name = "is_active", nullable = false)
    @Builder.Default
    @NotNull
    private Boolean isActive = true;

    @Column(name = "calculation_method", nullable = false, length = 30)
    @Builder.Default
    @NotNull
    private String calculationMethod = "PROGRESSIVE";

    // -------------------------------------------------------------------------
    // Unified Flat-Rate Fields (replaces ssf_* / pf_* dual-field system)
    // -------------------------------------------------------------------------

    /**
     * Employee-side flat rate as a percentage (e.g. 6.2 = 6.2%).
     * The calculation divides this by 100, consistent with PROGRESSIVE rate_percent.
     */
    @Column(name = "flat_employee_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    @NotNull
    private BigDecimal flatEmployeeRate = BigDecimal.ZERO;

    /**
     * Employer-side flat rate as a percentage (0 = no employer contribution).
     */
    @Column(name = "flat_employer_rate", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    @NotNull
    private BigDecimal flatEmployerRate = BigDecimal.ZERO;

    /**
     * Monthly wage cap for flat-rate calculation.
     * Null means no cap (unlimited).
     */
    @Column(name = "flat_cap_amount", precision = 19, scale = 4)
    private BigDecimal flatCapAmount;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_modified_by",
        foreignKey = @ForeignKey(name = "fk_tc_modified_by"))
    private CmEmployee lastModifiedBy;

    // -------------------------------------------------------------------------
    // Children
    // -------------------------------------------------------------------------

    @Builder.Default
    @OneToMany(mappedBy = "taxConfiguration", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<TaxSlab> taxSlabs = new ArrayList<>();
}
