package com.af.novadesk.api.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

/**
 * Lightweight entity mapping to the {@code fiscal_year_templates} table.
 * Holds country-specific fiscal year default data that is seeded into
 * the {@link FiscalYearSetting} table when a legal entity is approved.
 *
 * <p>This is a reference-data entity and does NOT extend {@link com.af.novadesk.api.common.entity.AbstractEntity}.</p>
 */
@Entity
@Table(name = "fiscal_year_templates", schema = "af_novadesk")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FiscalYearTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "country_code", length = 5, nullable = false, unique = true)
    private String countryCode;

    @Column(name = "fiscal_start_month", nullable = false)
    private int fiscalStartMonth;

    @Column(name = "fiscal_start_day", nullable = false)
    private int fiscalStartDay;

    @Column(name = "fiscal_end_month", nullable = false)
    private int fiscalEndMonth;

    @Column(name = "fiscal_end_day", nullable = false)
    private int fiscalEndDay;

    @Column(name = "periods_per_year", nullable = false)
    private int periodsPerYear;
}
