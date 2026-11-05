package com.af.novadesk.api.maintenance.entity;

import com.af.novadesk.api.asset.entity.Asset;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.maintenance.constants.MaintenancePriority;
import com.af.novadesk.api.maintenance.constants.MaintenanceStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Employee-submitted maintenance/repair request for an asset assigned to them.
 *
 * <p>Lifecycle: {@code SUBMITTED} → {@code IN_REVIEW} → {@code APPROVED} or
 * {@code REJECTED} → (if approved) {@code IN_PROGRESS} once a technician is
 * assigned → {@code COMPLETED}.</p>
 */
@Entity
@Table(
    name   = "mnt_maintenance_requests",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",                 name = "idx_mnt_req_asset"),
        @Index(columnList = "requested_by",              name = "idx_mnt_req_requester"),
        @Index(columnList = "organization_id, status",   name = "idx_mnt_req_org_status"),
    }
)
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@AttributeOverride(name = "status", column = @Column(name = "record_status"))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"asset"})
public class MaintenanceRequest extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_mnt_req_asset"))
    @NotNull
    private Asset asset;

    /** cm_employees.id — the employee who submitted the request. */
    @Column(name = "requested_by", nullable = false)
    @NotNull(message = "Requester is required")
    private UUID requestedBy;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    @Column(name = "description", nullable = false, length = 2000)
    @NotBlank(message = "Description is required")
    @Size(max = 2000)
    private String description;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "priority", nullable = false, length = 20)
    private MaintenancePriority priority = MaintenancePriority.MEDIUM;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private MaintenanceStatus maintenanceStatus = MaintenanceStatus.SUBMITTED;

    @Column(name = "cost_estimate", precision = 19, scale = 4)
    private BigDecimal costEstimate;

    /** cm_employees.id — nullable until ops assigns a technician. */
    @Column(name = "assigned_technician_id")
    private UUID assignedTechnicianId;

    @Column(name = "review_notes", length = 1000)
    @Size(max = 1000)
    private String reviewNotes;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
}
