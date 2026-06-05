package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.finance.entity.LegalEntity;
import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Payroll-specific employee record. References a {@link ShadowUser} (the base
 * identity from AuthHub) and adds entity assignment, compensation, and bank
 * details. Every Employee must first exist as a ShadowUser, but not all
 * ShadowUsers are Employees (e.g. investors, entity managers).
 *
 * <p>The {@code organizationId} and {@code authUserId} fields are denormalized
 * from the linked {@code ShadowUser} for query performance — the {@code @Filter}
 * annotation uses {@code organization_id} directly without a JOIN.</p>
 *
 * <p>When an employee is onboarded, the system automatically creates
 * {@code LeaveBalance} records for all three leave types (LLR-PAY-01.1).</p>
 *
 * <p>Relationships:
 * <ul>
 *   <li>{@code shadowUser}  – FK to the cached AuthHub identity</li>
 *   <li>{@code legalEntity} – the entity this employee belongs to</li>
 *   <li>{@code manager}     – self-referential FK for approval hierarchy (PAY-01.3)</li>
 * </ul>
 * </p>
 */
@Entity
@Table(name = "pr_employees", schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"shadow_user_id", "legal_entity_id"},
            name = "uk_emp_user_entity"),
        @UniqueConstraint(columnNames = {"employee_code", "legal_entity_id"},
            name = "uk_emp_code_entity")
    },
    indexes = {
        @Index(columnList = "legal_entity_id, status", name = "idx_emp_entity_status"),
        @Index(columnList = "organization_id", name = "idx_emp_org_id"),
        @Index(columnList = "shadow_user_id", name = "idx_emp_shadow_user")
    })
@Filter(name = "organizationFilter",
    condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"legalEntity", "shadowUser", "manager"})
public class Employee extends AbstractEntity {

    // -------------------------------------------------------------------------
    // Identity (ShadowUser Link)
    // -------------------------------------------------------------------------

    /**
     * The cached AuthHub identity that this Employee extends.
     * FK → shadow_users.id. Provides authUserId, email, displayName, organizationId.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shadow_user_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_emp_shadow_user"))
    @NotNull(message = "Shadow user is required")
    private ShadowUser shadowUser;

    /**
     * Denormalized from {@code ShadowUser.organizationId} for direct
     * {@code @Filter} usage without a JOIN to the identity schema.
     */
    @Column(name = "organization_id", nullable = false)
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /**
     * Denormalized from {@code ShadowUser.authUserId} for cross-module
     * references and outbox event payloads.
     */
    @Column(name = "auth_user_id", nullable = false)
    @NotNull(message = "Auth user ID is required")
    private UUID authUserId;

    // -------------------------------------------------------------------------
    // Entity Assignment
    // -------------------------------------------------------------------------

    /** The legal entity this employee belongs to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_emp_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    /** Unique employee code within the entity (e.g. "EMP-001"). */
    @Column(name = "employee_code", nullable = false, length = 50)
    @NotBlank(message = "Employee code is required")
    private String employeeCode;

    // -------------------------------------------------------------------------
    // Personal Details  (snapshot from ShadowUser at onboarding)
    // -------------------------------------------------------------------------

    @Column(name = "first_name", nullable = false, length = 100)
    @NotBlank(message = "First name is required")
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    @NotBlank(message = "Last name is required")
    private String lastName;

    @Column(name = "email", length = 255)
    private String email;

    // -------------------------------------------------------------------------
    // Employment Details
    // -------------------------------------------------------------------------

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "designation", length = 100)
    private String designation;

    /** Employee's hire date — used as the leave accrual start date. */
    @Column(name = "hire_date", nullable = false)
    @NotNull(message = "Hire date is required")
    private LocalDate hireDate;

    /** Null indicates an active employee. */
    @Column(name = "termination_date")
    private LocalDate terminationDate;

    // -------------------------------------------------------------------------
    // Manager Hierarchy  (LLR-PAY-01.3)
    // -------------------------------------------------------------------------

    /** Direct manager. Self-referential FK within the same table. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "manager_id",
        foreignKey = @ForeignKey(name = "fk_emp_manager"))
    private Employee manager;

    /**
     * Generated UUID when the employee is assigned a MANAGER entity role.
     * Null for non-manager employees. Populated during onboarding when the
     * "Assign Manager Role" toggle is enabled.
     */
    @Column(name = "manager_uuid", columnDefinition = "UUID")
    private UUID managerUuid;

    // -------------------------------------------------------------------------
    // Compensation
    // -------------------------------------------------------------------------

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    @NotNull(message = "Base salary is required")
    private BigDecimal baseSalary;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "salary_currency", nullable = false, length = 3, columnDefinition = "CHAR(3)")
    @NotBlank(message = "Salary currency is required")
    private String salaryCurrency;

    // -------------------------------------------------------------------------
    // Bank Details
    // -------------------------------------------------------------------------

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_ifsc_code", length = 20)
    private String bankIfscCode;
}
