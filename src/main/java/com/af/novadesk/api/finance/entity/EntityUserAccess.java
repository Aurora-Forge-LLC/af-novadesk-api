package com.af.novadesk.api.finance.entity;

import com.af.novadesk.api.identity.entity.ShadowUser;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;

/**
 * Grants a {@link ShadowUser} access to a specific {@link LegalEntity}.
 *
 * <p>The user reference is a {@link ShadowUser} — a local projection of the AuthHub
 * identity — rather than a direct foreign key into the AuthHub {@code users} table.
 * This decouples the Finance module from AuthHub's persistence layer while still
 * enabling JPA joins for entity-scoped queries and audit logs.</p>
 *
 * <p>Implements LLR-FIN-01.3:
 * <ul>
 *   <li>Entity selector shows only entities the user has permission to access —
 *       query {@code EntityUserAccess} by {@code shadowUser.authUserId}.</li>
 *   <li>All database queries filtered by selected entity context —
 *       resolved at the application layer using the active entity from this table.</li>
 *   <li>Entity context switches logged in the audit trail —
 *       {@code lastAccessedAt} is updated on every context switch.</li>
 * </ul>
 * </p>
 */
@Entity
@Table(
        name = "entity_user_accesses",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {"shadow_user_id", "legal_entity_id"},
                        name = "uk_entity_user_access"
                )
        }
)
@Filter(name = "organizationFilter",
        condition = "legal_entity_id IN (SELECT le.id FROM af_novadesk.legal_entities le " +
                    "WHERE le.organization_id = :orgId)")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"shadowUser", "legalEntity"})
public class EntityUserAccess extends AbstractEntity {

    // -------------------------------------------------------------------------
    // References
    // -------------------------------------------------------------------------

    /**
     * The local shadow projection of the AuthHub user being granted access.
     * Resolved from the JWT {@code sub} claim before this record is created.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shadow_user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_entity_access_shadow_user"))
    @NotNull(message = "Shadow user is required")
    private ShadowUser shadowUser;

    /** The entity the user is being granted access to. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_entity_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_entity_access_legal_entity"))
    @NotNull(message = "Legal entity is required")
    private LegalEntity legalEntity;

    // -------------------------------------------------------------------------
    // Access Control
    // -------------------------------------------------------------------------

    /**
     * Role this user holds within the entity context.
     * Expected values: "VIEWER", "EDITOR", "APPROVER", "ADMIN".
     * Kept as a String to allow RBAC expansion without schema changes.
     * Note: authoritative roles come from the JWT; this field drives
     * entity-scoped permission checks only.
     */
    @Column(name = "entity_role", nullable = false, length = 50)
    @NotNull(message = "Entity role is required")
    private String entityRole;

    // -------------------------------------------------------------------------
    // Session Tracking  (LLR-FIN-01.3)
    // -------------------------------------------------------------------------

    /**
     * Timestamp of the most recent entity context switch to this entity by this user.
     * Updated each time the user selects this entity from the entity selector.
     * Serves as both the audit trail entry (LLR-FIN-01.3) and the signal for
     * restoring the last-used entity on next login.
     */
    @Column(name = "last_accessed_at")
    private LocalDateTime lastAccessedAt;
}