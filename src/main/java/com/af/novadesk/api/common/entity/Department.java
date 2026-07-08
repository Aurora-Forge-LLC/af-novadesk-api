package com.af.novadesk.api.common.entity;

import org.hibernate.annotations.Filter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.UUID;

/**
 * A seeded department reference row, scoped to either an organization
 * ({@code legalEntityId == null}) or a specific legal entity
 * ({@code legalEntityId} set). Used to populate the department dropdown shown
 * when creating a user or employee — never free text.
 */
@Entity
@Table(name = "departments")
@Filter(name = "organizationFilter",
        condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class Department extends AbstractEntity {

    @Column(name = "organization_id", nullable = false)
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    /** NULL = org-level department; set = entity-level department scoped to this entity. */
    @Column(name = "legal_entity_id")
    private UUID legalEntityId;

    @Column(name = "name", nullable = false, length = 100)
    @NotBlank(message = "Department name is required")
    @Size(max = 100, message = "Department name must not exceed 100 characters")
    private String name;
}
