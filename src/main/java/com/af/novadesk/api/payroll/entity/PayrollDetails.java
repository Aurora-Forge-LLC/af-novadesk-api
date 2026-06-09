package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Salary and bank details for a payroll employee.
 *
 * <p>One row per employee. Linked to the primary entity assignment
 * ({@code is_primary_entity = true} in {@code cm_employee_entity_assignments})
 * that processes their payroll.</p>
 *
 * <p>References {@code cm_employees.id} and
 * {@code cm_employee_entity_assignments.id} as loose UUIDs — no JPA FK
 * to keep the payroll module decoupled from the common module.</p>
 */
@Entity
@Table(
    name   = "pr_payroll_details",
    schema = "af_novadesk",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "employee_id", name = "uk_pr_payroll_employee")
    }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PayrollDetails extends AbstractEntity {

    /** Loose reference to {@code cm_employees.id}. */
    @Column(name = "employee_id", nullable = false)
    @NotNull
    private UUID employeeId;

    /**
     * Loose reference to the primary {@code cm_employee_entity_assignments.id}.
     * This is the entity that owns and processes payroll for this employee.
     */
    @Column(name = "entity_assignment_id", nullable = false)
    @NotNull
    private UUID entityAssignmentId;

    // ── Compensation ──────────────────────────────────────────────────────────

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    @NotNull
    @Positive
    private BigDecimal baseSalary;

    @Column(name = "salary_currency", nullable = false, length = 3)
    @NotBlank
    private String salaryCurrency;

    @Builder.Default
    @Column(name = "pay_frequency", nullable = false, length = 20)
    private String payFrequency = "MONTHLY";

    // ── Bank Details ──────────────────────────────────────────────────────────

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_ifsc_code", length = 20)
    private String bankIfscCode;
}
