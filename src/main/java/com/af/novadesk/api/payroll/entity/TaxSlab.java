package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;

/**
 * A single progressive tax rate slab within a {@link TaxConfiguration}
 * (LLR-PAY-04.3–04.4). Examples:
 * <ul>
 *   <li>Nepal: NPR 0–500,000 → 1%, NPR 500,001–700,000 → 10%, etc.</li>
 *   <li>India: INR 0–250,000 → 0%, INR 250,001–500,000 → 5%, etc.</li>
 * </ul>
 *
 * <p>Slabs are ordered by {@code slabOrder}. The {@code incomeTo} is null
 * for the highest slab (unlimited). Tax is calculated on annual gross
 * salary and divided by 12 for monthly deduction.</p>
 */
@Entity
@Table(name = "pr_tax_slabs", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tax_configuration_id", "slab_order"},
            name = "uk_ts_config_order")
    })
@Filter(name = "organizationFilter",
    condition = "tax_configuration_id IN (SELECT tc.id FROM af_novadesk.pr_tax_configurations tc " +
                "JOIN af_novadesk.legal_entities le ON tc.legal_entity_id = le.id " +
                "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"taxConfiguration"})
public class TaxSlab extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tax_configuration_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_ts_tax_config"))
    @NotNull(message = "Tax configuration is required")
    private TaxConfiguration taxConfiguration;

    @Column(name = "slab_order", nullable = false)
    @NotNull
    private Integer slabOrder;                  // 1, 2, 3... for ordering

    @Column(name = "income_from", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal incomeFrom;              // Lower bound (inclusive)

    @Column(name = "income_to", precision = 19, scale = 4)
    private BigDecimal incomeTo;                // Upper bound (null = unlimited)

    @Column(name = "tax_rate", nullable = false, precision = 5, scale = 4)
    @NotNull
    private BigDecimal taxRate;                 // 0.01 for 1%, 0.10 for 10%, etc.

    @Column(name = "is_annual", nullable = false)
    @Builder.Default
    @NotNull
    private Boolean isAnnual = true;            // true = annual slab ÷ 12 for monthly

    @Column(name = "description", length = 200)
    private String description;                 // e.g. "NPR 0 - 500,000: 1%"
}
