package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.AssetCategory;
import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.DepreciationMethod;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.common.entity.LegalEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Core asset registry record (LLR-AST-01).
 *
 * <p>Every physical asset owned by a legal entity is registered here.
 * Serial numbers are unique within an organisation (case-insensitive,
 * whitespace-trimmed) and enforced at the DB level.</p>
 *
 * <p>Upon registration the system generates a QR code label and publishes
 * a {@code HardwarePurchased} outbox event so the accounting module can
 * create the corresponding fixed-asset journal entry.</p>
 */
@Entity
@Table(
    name    = "ast_assets",
    schema  = "af_novadesk",
    indexes = {
        @Index(columnList = "legal_entity_id",          name = "idx_ast_assets_entity"),
        @Index(columnList = "organization_id",           name = "idx_ast_assets_org"),
        @Index(columnList = "organization_id, status",   name = "idx_ast_assets_status"),
        @Index(columnList = "organization_id, category", name = "idx_ast_assets_category"),
        @Index(columnList = "serial_number",             name = "idx_ast_assets_serial"),
    },
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"organization_id", "serial_number"},
        name        = "uk_ast_serial_number"
    )
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@AttributeOverride(name = "status", column = @Column(name = "record_status"))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "assignments", "custodyTransfers", "depreciationSchedules"})
public class Asset extends AbstractEntity {

    // ── Org / Entity scope ────────────────────────────────────────────────────

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_asset_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    @Column(name = "organization_id", nullable = false)
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    // ── Classification ────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 20)
    @NotNull(message = "Asset category is required")
    private AssetCategory category;

    @Column(name = "asset_type", nullable = false, length = 150)
    @NotBlank(message = "Asset type is required")
    private String assetType;

    @Column(name = "manufacturer", length = 150)
    private String manufacturer;

    @Column(name = "model_number", length = 150)
    private String modelNumber;

    /** Unique within the organisation — case-insensitive, whitespace-trimmed. */
    @Column(name = "serial_number", nullable = false, length = 100)
    @NotBlank(message = "Serial number is required")
    @Size(max = 100, message = "Serial number must not exceed 100 characters")
    private String serialNumber;

    // ── Purchase ──────────────────────────────────────────────────────────────

    @Column(name = "purchase_date", nullable = false)
    @NotNull(message = "Purchase date is required")
    private LocalDate purchaseDate;

    @Column(name = "purchase_cost", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Purchase cost is required")
    @DecimalMin(value = "0.01", message = "Purchase cost must be greater than zero")
    private BigDecimal purchaseCost;

    @Column(name = "currency_code", nullable = false, length = 3)
    @NotBlank(message = "Currency code is required")
    private String currencyCode;

    @Column(name = "vendor", length = 200)
    private String vendor;

    // ── Warranty ──────────────────────────────────────────────────────────────

    @Column(name = "warranty_expiry_date")
    private LocalDate warrantyExpiryDate;

    // ── Depreciation ──────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "depreciation_method", nullable = false, length = 30)
    @NotNull(message = "Depreciation method is required")
    private DepreciationMethod depreciationMethod;

    @Column(name = "useful_life_years", nullable = false)
    @NotNull(message = "Useful life is required")
    @Min(value = 1, message = "Useful life must be at least 1 year")
    private Integer usefulLifeYears;

    @Column(name = "net_book_value", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal netBookValue;

    @Builder.Default
    @Column(name = "accumulated_depreciation", nullable = false, precision = 19, scale = 4)
    private BigDecimal accumulatedDepreciation = BigDecimal.ZERO;

    // ── Location & Status ─────────────────────────────────────────────────────

    @Column(name = "current_location", length = 300)
    private String currentLocation;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private AssetStatus assetStatus = AssetStatus.AVAILABLE;

    // ── Supporting ────────────────────────────────────────────────────────────

    @Column(name = "notes", length = 1000)
    @Size(max = 1000)
    private String notes;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    @Column(name = "qr_code_url", length = 500)
    private String qrCodeUrl;

    // ── Relationships ─────────────────────────────────────────────────────────

    @Builder.Default
    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AssetAssignment> assignments = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AssetCustodyTransfer> custodyTransfers = new ArrayList<>();

    @Builder.Default
    @OneToMany(mappedBy = "asset", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<DepreciationSchedule> depreciationSchedules = new ArrayList<>();
}
