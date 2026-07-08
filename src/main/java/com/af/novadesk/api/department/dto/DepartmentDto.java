package com.af.novadesk.api.department.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Read-only projection of a {@link com.af.novadesk.api.common.entity.Department}
 * used to populate the department dropdown at user/employee creation time.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentDto {

    private UUID id;
    private String name;

    /** NULL for an org-level department; set for an entity-level department. */
    private UUID legalEntityId;
}
