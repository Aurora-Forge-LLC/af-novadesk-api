package com.af.novadesk.api.common.entity;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.util.UUID;

/**
 * Canonical employee identity record — one row per person per organisation.
 *
 * <p>Shared across all modules (asset, payroll, leave, offboarding). Module-specific
 * data (salary, leave balances, asset assignments) lives in separate tables and
 * references this record via {@code employee_id}.</p>
 *
 * <p>An employee may be assigned to multiple legal entities via
 * {@link CmEmployeeEntityAssignment}. The primary entity is the one that
 * processes their payroll ({@code is_primary_entity = true}).</p>
 */
@Entity
@Table(
    name   = "cm_employees",
    schema = "af_novadesk",
    indexes = {
        @Index(columnList = "organization_id",        name = "idx_cm_emp_org"),
        @Index(columnList = "auth_user_id",           name = "idx_cm_emp_auth_user"),
        @Index(columnList = "organization_id, status", name = "idx_cm_emp_status"),
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"organization_id", "auth_user_id"}, name = "uk_cm_emp_auth_user"),
        @UniqueConstraint(columnNames = {"organization_id", "employee_code"}, name = "uk_cm_emp_code"),
    }
)
@Filter(name = "organizationFilter", condition = "organization_id = :orgId")
@AttributeOverride(name = "status", column = @Column(name = "record_status"))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CmEmployee extends AbstractEntity {

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    /** Links to {@code shadow_users.auth_user_id} — the canonical AuthHub identity. */
    @Column(name = "auth_user_id", nullable = false)
    @NotNull
    private UUID authUserId;

    @Column(name = "employee_code", nullable = false, length = 50)
    @NotBlank
    @Size(max = 50)
    private String employeeCode;

    @Column(name = "display_name", nullable = false, length = 200)
    @NotBlank
    @Size(max = 200)
    private String displayName;

    @Column(name = "email", length = 255)
    @Size(max = 255)
    private String email;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EmployeeStatus employeeStatus = EmployeeStatus.ACTIVE;
}
