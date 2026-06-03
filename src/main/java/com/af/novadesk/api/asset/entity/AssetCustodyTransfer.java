package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.CustodianType;
import com.af.novadesk.api.asset.constants.CustodyTransferType;
import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Immutable append-only chain-of-custody audit log (LLR-AST-02.3).
 *
 * <p>A new row is inserted for every custodian change — assignment, return,
 * reassignment, write-off, and disposal. Rows are <strong>never updated or
 * deleted</strong>. This provides a complete, tamper-evident custody trail
 * for compliance and audit purposes.</p>
 */
@Entity
@Table(
    name   = "ast_custody_transfers",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",       name = "idx_ast_ct_asset"),
        @Index(columnList = "organization_id", name = "idx_ast_ct_org"),
    }
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"asset"})
public class AssetCustodyTransfer extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_ct_asset"))
    @NotNull
    private Asset asset;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    // ── From ──────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "from_custodian_type", nullable = false, length = 20)
    @NotNull(message = "From custodian type is required")
    private CustodianType fromCustodianType;

    /** cm_employees.id — null when from_custodian_type = IT_DEPARTMENT. */
    @Column(name = "from_custodian_id")
    private UUID fromCustodianId;

    // ── To ────────────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "to_custodian_type", nullable = false, length = 20)
    @NotNull(message = "To custodian type is required")
    private CustodianType toCustodianType;

    /** cm_employees.id — null when to_custodian_type = IT_DEPARTMENT. */
    @Column(name = "to_custodian_id")
    private UUID toCustodianId;

    // ── Transfer context ──────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "transfer_type", nullable = false, length = 20)
    @NotNull(message = "Transfer type is required")
    private CustodyTransferType transferType;

    @Column(name = "transfer_date", nullable = false)
    @NotNull(message = "Transfer date is required")
    private LocalDate transferDate;

    /** cm_employees.id — IT admin who approved the transfer. */
    @Column(name = "approved_by", nullable = false)
    @NotNull(message = "Approver is required")
    private UUID approvedBy;

    @Column(name = "notes", length = 500)
    @Size(max = 500)
    private String notes;
}
