package com.af.novadesk.api.finance.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request payload for inviting a brand-new user directly into an entity role.
 *
 * <p>Drives the one-call invite flow: novadesk-api provisions the user in
 * AuthHub (which emails the password-setup invite) and then grants the
 * requested entity role — see
 * {@code POST /api/v1/legal-entities/{id}/access/invite}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EntityUserInviteRequest {

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    private String email;

    @NotBlank(message = "First name is required")
    @Size(min = 2, max = 50, message = "First name must be 2-50 characters")
    private String firstName;

    @NotBlank(message = "Last name is required")
    @Size(min = 2, max = 50, message = "Last name must be 2-50 characters")
    private String lastName;

    @NotBlank(message = "Entity role is required")
    @Pattern(
            regexp = "^(ENTITY_ADMIN|FINANCE_MANAGER|HR_MANAGER|IT_ADMIN|MANAGER|ACCOUNTANT|EMPLOYEE)$",
            message = "Entity role must be one of: ENTITY_ADMIN, FINANCE_MANAGER, HR_MANAGER, IT_ADMIN, MANAGER, ACCOUNTANT, EMPLOYEE"
    )
    private String entityRole;

    /** Optional — may be null for initial admin setup; required for regular employees/users. */
    private UUID departmentId;
}
