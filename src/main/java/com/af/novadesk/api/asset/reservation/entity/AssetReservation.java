package com.af.novadesk.api.asset.reservation.entity;

import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.asset.reservation.constants.ReservationStatus;
import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A time-boxed reservation of a shared asset by an employee (LLR-AST-05).
 *
 * <p>Employees request a window during which they need a shared asset
 * (projector, pool car, meeting kit). Ops approve or reject the request.
 * A reservation that is never decided auto-expires once its window has
 * passed — see {@code ReservationExpiryScheduler}.</p>
 *
 * <p>Timestamps are stored in UTC to keep the approval and expiry windows
 * stable across the deployment's server timezone.</p>
 */
@Entity
@Table(
    name   = "ast_asset_reservations",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",                    name = "idx_ast_rsv_asset"),
        @Index(columnList = "requested_by",                name = "idx_ast_rsv_requester"),
        @Index(columnList = "organization_id, status",     name = "idx_ast_rsv_org_status"),
        @Index(columnList = "asset_id, starts_at, ends_at", name = "idx_ast_rsv_asset_window"),
    }
)
@AttributeOverride(name = "status", column = @Column(name = "record_status"))
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"asset"})
public class AssetReservation extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_rsv_asset"))
    @NotNull
    private Asset asset;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    /** cm_employees.id — the employee who requested the reservation. */
    @Column(name = "requested_by", nullable = false)
    @NotNull(message = "Requester is required")
    private UUID requestedBy;

    // ── Window ────────────────────────────────────────────────────────────────

    @Column(name = "starts_at", nullable = false)
    @NotNull(message = "Reservation start is required")
    private LocalDateTime startsAt;

    @Column(name = "ends_at", nullable = false)
    @NotNull(message = "Reservation end is required")
    private LocalDateTime endsAt;

    @Column(name = "purpose", length = 300)
    @Size(max = 300)
    private String purpose;

    // ── Decision ──────────────────────────────────────────────────────────────

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ReservationStatus reservationStatus = ReservationStatus.PENDING;

    /** cm_employees.id — the ops user who approved/rejected the request. */
    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "decision_notes", length = 500)
    @Size(max = 500)
    private String decisionNotes;
}
