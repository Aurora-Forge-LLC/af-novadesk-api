package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Year-by-year depreciation schedule for an asset (LLR-AST-04).
 *
 * <p>One row per asset per fiscal year. Created when the asset is registered
 * (full schedule generated upfront) or when the year-end scheduler runs.
 * The {@code journalEntryId} is a loose reference to {@code fa_ledger_entries}
 * — not enforced as a foreign key since the accounting module owns that table.</p>
 */
@Entity
@Table(
    name   = "ast_depreciation_schedules",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",                           name = "idx_ast_depr_asset"),
        @Index(columnList = "organization_id, fiscal_year",       name = "idx_ast_depr_org"),
    },
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"asset_id", "fiscal_year"},
        name        = "uk_ast_depr_asset_year"
    )
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"asset"})
public class DepreciationSchedule extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_depr_asset"))
    @NotNull
    private Asset asset;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    @Column(name = "fiscal_year", nullable = false)
    @NotNull(message = "Fiscal year is required")
    @Min(value = 2000, message = "Fiscal year must be 2000 or later")
    private Integer fiscalYear;

    @Column(name = "annual_depreciation", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal annualDepreciation;

    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal accumulatedDepreciation;

    @Column(name = "net_book_value", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal netBookValue;

    @Builder.Default
    @Column(name = "is_posted", nullable = false)
    private boolean posted = false;

    /**
     * Loose reference to {@code fa_ledger_entries.id}.
     * Not enforced as a FK — the accounting module owns that record.
     */
    @Column(name = "journal_entry_id")
    private UUID journalEntryId;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;
}
