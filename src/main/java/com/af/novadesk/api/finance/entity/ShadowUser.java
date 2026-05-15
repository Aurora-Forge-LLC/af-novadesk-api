package com.af.novadesk.api.finance.entity;



import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A local projection of an AuthHub identity, scoped to the Finance module.
 *
 * <p><strong>Source of truth:</strong> AuthHub owns the full user lifecycle.
 * This record caches only the JWT claims the Finance domain needs for joins,
 * audit logs, and entity-scoped access checks — no auth logic lives here.</p>
 *
 * <p><strong>Sync strategy (lazy upsert):</strong> On every authenticated request
 * the JWT filter extracts {@code sub}, {@code email}, {@code organizationId}, and
 * optional {@code name} claims from the verified token and calls
 * {@code ShadowUserSyncService.upsert(...)}. No extra AuthHub network call is made —
 * all data arrives in the token itself.</p>
 *
 * <p><strong>organizationId:</strong> Taken from the {@code organizationId} claim in
 * the JWT. The Finance module uses this to enforce org-level data isolation — all
 * entity-scoped queries are implicitly filtered by the caller's organization.</p>
 *
 * <p><strong>Intentionally excluded:</strong> password hashes, MFA config, failed
 * login attempts, roles/permissions (re-read from JWT on each request, never stored).</p>
 */
@Entity
@Table(
        name = "shadow_users",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = "auth_user_id",    name = "uk_shadow_user_auth_id"),
                @UniqueConstraint(columnNames = "email",           name = "uk_shadow_user_email")
        },
        indexes = {
                @Index(columnList = "organization_id", name = "idx_shadow_user_org_id")
        }
)
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString
public class ShadowUser extends AbstractEntity {

    // -------------------------------------------------------------------------
    // AuthHub Link  —  canonical cross-service identity
    // -------------------------------------------------------------------------

    /**
     * The {@code sub} claim from the AuthHub JWT.
     * Immutable after creation — this is the stable identity key across all services.
     */
    @Column(name = "auth_user_id", nullable = false, updatable = false,
            columnDefinition = "UUID")
    @NotNull(message = "AuthHub user ID is required")
    private UUID authUserId;

    // -------------------------------------------------------------------------
    // Organization Scope  —  from JWT `organizationId` claim
    // -------------------------------------------------------------------------

    /**
     * The organization this user belongs to, taken from the {@code organizationId}
     * JWT claim. Used to scope all Finance module queries to the correct organization.
     *
     * <p>This is not a FK to a local organization table — the organization aggregate
     * is owned by AuthHub. It is stored here purely as a scoping/filtering value.</p>
     *
     * <p>Indexed (not unique) because one organization has many shadow users.</p>
     */
    @Column(name = "organization_id", nullable = false, updatable = false,
            columnDefinition = "UUID")
    @NotNull(message = "Organization ID is required")
    private UUID organizationId;

    // -------------------------------------------------------------------------
    // Cached Claims  —  denormalised for display and audit
    // -------------------------------------------------------------------------

    /**
     * Cached from the {@code email} JWT claim.
     * Used in audit logs and Finance notifications so no AuthHub call is needed
     * to show "approved by user@example.com". Synced on every authenticated request.
     */
    @Column(nullable = false, length = 255)
    @NotBlank(message = "Email is required")
    @Email(message = "Email should be valid")
    private String email;

    /**
     * Optional display name cached from JWT claims (first + last, or username).
     * Null if AuthHub does not include a name claim.
     */
    @Column(name = "display_name", length = 150)
    private String displayName;

    // -------------------------------------------------------------------------
    // Sync Metadata
    // -------------------------------------------------------------------------

    /**
     * Timestamp of the last successful upsert from a JWT claim.
     * Useful for detecting stale projections and debugging identity drift.
     */
    @Column(name = "last_synced_at", nullable = false)
    @NotNull
    private LocalDateTime lastSyncedAt;
}