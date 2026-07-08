package com.af.novadesk.api.department.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Request payload for creating or updating a department.
 *
 * <p>When {@code legalEntityId} is {@code null}, the department is created at
 * the organization level (visible across all entities). When set, the department
 * is scoped to that specific legal entity.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentCreateRequest {

    @NotBlank(message = "Department name is required")
    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String name;

    /**
     * {@code null} = org-level department; set = entity-level department scoped
     * to this legal entity.
     */
    private UUID legalEntityId;
}
