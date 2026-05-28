package com.af.novadesk.api.finance.entity;



import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

/**
 * Stores the fiscal-year configuration for a {@link LegalEntity}.
 *
 * <p>Separated from {@code LegalEntity} to satisfy 3NF: fiscal-year attributes
 * (start/end month, reporting period) depend solely on this record's PK and are
 * country-regulation-driven, not entity-identity-driven.</p>
 *
 * <p>Implements LLR-FIN-01.2: "Initialize fiscal year settings based on country regulations."</p>
 *
 * <p>Examples by country:
 * <ul>
 *   <li>US  – Jan 1 → Dec 31 (calendar year)</li>
 *   <li>IN  – Apr 1 → Mar 31</li>
 *   <li>NP  – Mid-Jul → Mid-Jul (Bikram Sambat; approximated as Jul 16 → Jul 15)</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "fiscal_year_settings")
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity"})
public class FiscalYearSetting extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Parent Reference
    // -------------------------------------------------------------------------

    /** The entity this fiscal-year configuration belongs to. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_fiscal_year_legal_entity"))
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Fiscal Period Definition
    // -------------------------------------------------------------------------

    /**
     * Month (1–12) on which the fiscal year starts.
     * E.g. 1 = January (US), 4 = April (India).
     */
    @Column(name = "fiscal_start_month", nullable = false)
    @NotNull
    @Min(1)
    @Max(12)
    private Integer fiscalStartMonth;

    /**
     * Day-of-month on which the fiscal year starts.
     * Accommodates Nepal's mid-month fiscal year (typically day 16).
     */
    @Column(name = "fiscal_start_day", nullable = false)
    @NotNull
    @Min(1)
    @Max(31)
    private Integer fiscalStartDay;

    /**
     * Month (1–12) on which the fiscal year ends.
     * E.g. 12 = December (US), 3 = March (India).
     */
    @Column(name = "fiscal_end_month", nullable = false)
    @NotNull
    @Min(1)
    @Max(12)
    private Integer fiscalEndMonth;

    /**
     * Day-of-month on which the fiscal year ends.
     */
    @Column(name = "fiscal_end_day", nullable = false)
    @NotNull
    @Min(1)
    @Max(31)
    private Integer fiscalEndDay;

    /**
     * Calendar year from which the current active fiscal year starts.
     * Used to construct the absolute date range when generating period reports.
     */
    @Column(name = "current_fiscal_year", nullable = false)
    @NotNull
    private Integer currentFiscalYear;

    /**
     * Number of accounting periods per fiscal year (e.g. 12 for monthly, 4 for quarterly).
     */
    @Builder.Default
    @Column(name = "periods_per_year", nullable = false)
    private Integer periodsPerYear = 12;
}
