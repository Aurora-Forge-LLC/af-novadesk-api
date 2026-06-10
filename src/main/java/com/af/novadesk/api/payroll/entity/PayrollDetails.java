package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Payroll-specific compensation and bank details for an employee.
 *
 * <p>One row per employee, linked to {@code cm_employees.id} via
 * {@link #employeeId} (loose UUID reference, no JPA FK). Salary,
 * currency, bank account, and pay frequency are stored here rather
 * than on the canonical {@code CmEmployee}.
 *
 * @see com.af.novadesk.api.common.entity.CmEmployee
 */
@Entity
@Table(name = "pr_payroll_details", schema = "af_novadesk",
       uniqueConstraints = {
           @UniqueConstraint(columnNames = {"employee_id"},
               name = "uk_pr_payroll_employee")
       })
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

    /** Loose reference to the primary entity assignment. */
    @Column(name = "entity_assignment_id", nullable = false)
    @NotNull
    private UUID entityAssignmentId;

    @Column(name = "base_salary", nullable = false, precision = 19, scale = 4)
    @NotNull
    private BigDecimal baseSalary;

    @Column(name = "salary_currency", nullable = false, length = 3)
    @NotNull
    private String salaryCurrency;

    @Column(name = "pay_frequency", nullable = false, length = 20)
    @Builder.Default
    @NotNull
    private String payFrequency = "MONTHLY";

    @Column(name = "bank_account_number", length = 50)
    private String bankAccountNumber;

    @Column(name = "bank_name", length = 150)
    private String bankName;

    @Column(name = "bank_ifsc_code", length = 20)
    private String bankIfscCode;

    @Column(name = "record_status", nullable = false, length = 20)
    @Builder.Default
    @NotNull
    private String recordStatus = "ACTIVE";
}
