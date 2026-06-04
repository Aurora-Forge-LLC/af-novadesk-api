package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.AcknowledgmentStatus;
import com.af.novadesk.api.asset.constants.AssignmentPurpose;
import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.constants.ConditionGrade;
import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Records the assignment of an asset to an employee (LLR-AST-02).
 *
 * <p>Contains all business logic for the assignment lifecycle including
 * the digital acknowledgment workflow. This is separate from
 * {@link AssetCustodyTransfer} which is the immutable audit log.</p>
 *
 * <p>Acknowledgment flow:
 * <ol>
 *   <li>Assignment created → {@code acknowledgmentStatus = PENDING}</li>
 *   <li>Email sent to employee with one-time {@code acknowledgmentToken}</li>
 *   <li>Employee visits acknowledgment page → status → {@code ACKNOWLEDGED}</li>
 *   <li>Asset status → {@code ASSIGNED}</li>
 * </ol>
 * </p>
 */
@Entity
@Table(
    name   = "ast_asset_assignments",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",                    name = "idx_ast_asgn_asset"),
        @Index(columnList = "employee_id",                 name = "idx_ast_asgn_employee"),
        @Index(columnList = "organization_id, status",     name = "idx_ast_asgn_org_status"),
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
@ToString(exclude = {"asset", "assetReturn"})
public class AssetAssignment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_asgn_asset"))
    @NotNull
    private Asset asset;

    /** cm_employees.id — the employee receiving the asset. */
    @Column(name = "employee_id", nullable = false)
    @NotNull(message = "Employee is required")
    private UUID employeeId;

    /** cm_employees.id — the IT admin performing the assignment. */
    @Column(name = "assigned_by", nullable = false)
    @NotNull(message = "Assigned-by is required")
    private UUID assignedBy;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    // ── Assignment details ────────────────────────────────────────────────────

    @Column(name = "assignment_date", nullable = false)
    @NotNull(message = "Assignment date is required")
    private LocalDate assignmentDate;

    @Column(name = "expected_return_date")
    private LocalDate expectedReturnDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 30)
    private AssignmentPurpose purpose = AssignmentPurpose.PRIMARY_WORK;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "condition_at_assignment", nullable = false, length = 10)
    private ConditionGrade conditionAtAssignment = ConditionGrade.GOOD;

    // ── Acknowledgment ────────────────────────────────────────────────────────

    @Builder.Default
    @Column(name = "requires_acknowledgment", nullable = false)
    private boolean requiresAcknowledgment = true;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "acknowledgment_status", nullable = false, length = 20)
    private AcknowledgmentStatus acknowledgmentStatus = AcknowledgmentStatus.PENDING;

    @Column(name = "acknowledgment_at")
    private LocalDateTime acknowledgmentAt;

    @Column(name = "acknowledgment_ip", length = 50)
    private String acknowledgmentIp;

    /** One-time token emailed to the employee. Expires after 7 days. */
    @Column(name = "acknowledgment_token", length = 200)
    private String acknowledgmentToken;

    @Column(name = "acknowledgment_token_expires_at")
    private LocalDateTime acknowledgmentTokenExpiresAt;

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AssignmentStatus assignmentStatus = AssignmentStatus.ACTIVE;

    @Column(name = "notes", length = 500)
    @Size(max = 500)
    private String notes;

    // ── Relationship ──────────────────────────────────────────────────────────

    @OneToOne(mappedBy = "assignment", fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    private AssetReturn assetReturn;
}
