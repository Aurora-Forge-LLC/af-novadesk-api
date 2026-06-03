package com.af.novadesk.api.asset.entity;

import com.af.novadesk.api.asset.constants.ConditionGrade;
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
 * Records the physical return of an asset from an employee (LLR-AST-03.3).
 *
 * <p>One-to-one with {@link AssetAssignment} — once an asset is returned
 * for a given assignment, a return record is created and the assignment
 * status transitions to {@code RETURNED}.</p>
 */
@Entity
@Table(
    name   = "ast_asset_returns",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "asset_id",       name = "idx_ast_returns_asset"),
        @Index(columnList = "organization_id", name = "idx_ast_returns_org"),
    },
    uniqueConstraints = @UniqueConstraint(
        columnNames = {"assignment_id"},
        name        = "uk_ast_return_assignment"
    )
)
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"asset", "assignment"})
public class AssetReturn extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "asset_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_return_asset"))
    @NotNull
    private Asset asset;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assignment_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_ast_return_assignment"))
    @NotNull
    private AssetAssignment assignment;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    @Column(name = "return_date", nullable = false)
    @NotNull(message = "Return date is required")
    private LocalDate returnDate;

    /** cm_employees.id — employee returning the asset. */
    @Column(name = "returned_by_employee_id", nullable = false)
    @NotNull(message = "Returning employee is required")
    private UUID returnedByEmployeeId;

    /** cm_employees.id — IT admin receiving the asset. */
    @Column(name = "received_by_employee_id", nullable = false)
    @NotNull(message = "Receiving employee is required")
    private UUID receivedByEmployeeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "condition_at_return", nullable = false, length = 10)
    @NotNull(message = "Condition at return is required")
    private ConditionGrade conditionAtReturn;

    @Column(name = "repair_required", nullable = false)
    private boolean repairRequired;

    @Column(name = "notes", length = 1000)
    @Size(max = 1000)
    private String notes;
}
