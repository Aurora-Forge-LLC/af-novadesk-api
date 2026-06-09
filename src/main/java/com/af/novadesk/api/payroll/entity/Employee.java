package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.util.UUID;

/**
 * Thin payroll employee record — Phase 5 slimmed version.
 *
 * <p>After the common-module refactoring, this entity holds only what is
 * payroll-module-specific:</p>
 * <ul>
 *   <li>{@code cmEmployeeId} — loose UUID reference to {@code cm_employees.id}
 *       (canonical identity: name, email, employee code, org)</li>
 *   <li>{@code manager} — self-referential FK for the payroll approval hierarchy</li>
 * </ul>
 *
 * <p>Identity data → {@code cm_employees}<br>
 *    Entity assignments (dept, hire date) → {@code cm_employee_entity_assignments}<br>
 *    Compensation (salary, bank) → {@code pr_payroll_details}</p>
 */
@Entity
@Table(name = "pr_employees", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "organization_id",  name = "idx_emp_org_id"),
        @Index(columnList = "cm_employee_id",   name = "idx_emp_cm_employee_id")
    })
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"manager"})
public class Employee extends AbstractEntity {

    /** Org scope — required for the Hibernate @Filter. */
    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    /**
     * Loose reference to {@code cm_employees.id}.
     * Use {@code CmEmployeeRepository} to resolve identity (name, email, code).
     */
    @Column(name = "cm_employee_id")
    private UUID cmEmployeeId;

    /** Direct manager — self-referential within payroll module for approval hierarchy. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id", foreignKey = @ForeignKey(name = "fk_emp_manager"))
    private Employee manager;
}
