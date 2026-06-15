package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.AssetStatus;
import com.af.novadesk.api.asset.constants.WriteOffAction;
import com.af.novadesk.api.asset.constants.WriteOffReason;
import com.af.novadesk.api.asset.constants.WriteOffStatus;
import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Executive override for lost or unrecoverable assets during employee offboarding
 * (LLR-AST-03.5).
 *
 * <p>When an employee cannot return an asset (lost, stolen, unrecoverable), the
 * IT admin raises a write-off request. A Finance Manager or C-level executive
 * then reviews and chooses one of three actions: WRITE_OFF, DEDUCT_FROM_PAY,
 * or REQUIRE_REIMBURSEMENT.</p>
 *
 * <p>Once the write-off reaches status {@code APPROVED}, the offboarding gate
 * is unblocked for the employee.</p>
 */
@Entity
@Table(
    name   = "ast_write_offs",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",                name = "idx_ast_wo_asset"),
        @Index(columnList = "organization_id, status", name = "idx_ast_wo_org"),
    },
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"asset_id"},
        name        = "uk_ast_write_off_asset"
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
@ToString(exclude = {"asset"})
public class AssetWriteOff extends AbstractEntity {

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_wo_asset"))
    @NotNull
    private Asset asset;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    // ── Request ───────────────────────────────────────────────────────────────

    /** cm_employees.id — IT admin raising the write-off request. */
    @Column(name = "requested_by", nullable = false)
    @NotNull(message = "Requester is required")
    private UUID requestedBy;

    /** cm_employees.id — employee who last held the asset. */
    @Column(name = "last_custodian_id", nullable = false)
    @NotNull(message = "Last custodian is required")
    private UUID lastCustodianId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 20)
    @NotNull(message = "Reason is required")
    private WriteOffReason reason;

    @Column(name = "depreciated_value", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Depreciated value is required")
    @DecimalMin(value = "0.00", message = "Depreciated value must be zero or greater")
    private BigDecimal depreciatedValue;

    /** Asset status at the time the write-off was requested — restored if the write-off is rejected. */
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_asset_status", nullable = false, length = 20)
    @NotNull(message = "Previous asset status is required")
    private AssetStatus previousAssetStatus;

    // ── Decision ──────────────────────────────────────────────────────────────

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 30)
    private WriteOffAction action;

    /** cm_employees.id — Finance Manager or C-level executive. */
    @Column(name = "approved_by")
    private UUID approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WriteOffStatus writeOffStatus = WriteOffStatus.PENDING;

    @Column(name = "audit_notes", length = 1000)
    @Size(max = 1000)
    private String auditNotes;
}
