package com.af.novadesk.api.common.entity;

import com.af.novadesk.api.common.constants.EmployeeAssignmentStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Bridge table — many-to-many between {@link CmEmployee} and {@link LegalEntity}.
 *
 * <p>One row per (employee, entity) pair. An employee working across two entities
 * has two rows here but a single row in {@code cm_employees}.</p>
 *
 * <p>{@code isPrimaryEntity = true} marks the entity responsible for this
 * employee's payroll. Enforced unique per employee via a partial unique index.</p>
 */
@Entity
@Table(
    name   = "cm_employee_entity_assignments",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "employee_id",             name = "idx_cm_ea_employee"),
        @Index(columnList = "legal_entity_id, status", name = "idx_cm_ea_entity"),
        @Index(columnList = "organization_id",         name = "idx_cm_ea_org"),
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"employee_id", "legal_entity_id"}, name = "uk_cm_emp_entity"),
    }
)
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@AttributeOverride(name = "status", column = @Column(name = "record_status"))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"employee", "legalEntity"})
public class CmEmployeeEntityAssignment extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cm_ea_employee"))
    @NotNull
    private CmEmployee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_cm_ea_entity"))
    @NotNull
    private LegalEntity legalEntity;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    /** Deprecated — historical free-text value; new writes use {@link #departmentId}. */
    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "department_id")
    private UUID departmentId;

    @Column(name = "designation", length = 100)
    private String designation;

    /** TRUE for the entity that processes this employee's payroll. Unique per employee. */
    @Builder.Default
    @Column(name = "is_primary_entity", nullable = false)
    private boolean primaryEntity = false;

    @Column(name = "hire_date", nullable = false)
    @NotNull
    private LocalDate hireDate;

    @Column(name = "termination_date")
    private LocalDate terminationDate;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmployeeAssignmentStatus assignmentStatus = EmployeeAssignmentStatus.ACTIVE;
}
