package com.af.novadesk.api.common.entity;

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
 * <p>Moved to {@code common} module — shared across finance and payroll modules.</p>
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

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "fk_fiscal_year_legal_entity"))
    private LegalEntity legalEntity;

    @Column(name = "fiscal_start_month", nullable = false)
    @NotNull @Min(1) @Max(12)
    private Integer fiscalStartMonth;

    @Column(name = "fiscal_start_day", nullable = false)
    @NotNull @Min(1) @Max(31)
    private Integer fiscalStartDay;

    @Column(name = "fiscal_end_month", nullable = false)
    @NotNull @Min(1) @Max(12)
    private Integer fiscalEndMonth;

    @Column(name = "fiscal_end_day", nullable = false)
    @NotNull @Min(1) @Max(31)
    private Integer fiscalEndDay;

    @Column(name = "current_fiscal_year", nullable = false)
    @NotNull
    private Integer currentFiscalYear;

    @Builder.Default
    @Column(name = "periods_per_year", nullable = false)
    private Integer periodsPerYear = 12;
}
