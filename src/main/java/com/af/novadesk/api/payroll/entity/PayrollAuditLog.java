package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.payroll.constants.PayrollAuditAction;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * Immutable audit log for all state transitions across the payroll module
 * (LLR-PAY-02.3 waive, PAY-04.6 tax config changes, cross-cutting).
 *
 * <p>Stores a JSON snapshot of the entity state before the change for
 * full audit trail compliance.</p>
 */
@Entity
@Table(name = "pr_payroll_audit_logs", schema = "af_novadesk",
    indexes = {
        @Index(columnList = "entity_type, entity_id", name = "idx_pal_entity"),
        @Index(columnList = "created_at", name = "idx_pal_created")
    })
@Filter(name = "organizationFilter",
    condition = "organization_id = :orgId")
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PayrollAuditLog extends AbstractEntity {

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 30)
    @NotNull
    private PayrollAuditAction action;

    @Column(name = "entity_type", nullable = false, length = 50)
    @NotBlank
    private String entityType;                  // e.g. "LeaveRequest", "PayrollBatch"

    @Column(name = "entity_id", nullable = false)
    @NotNull
    private UUID entityId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "performed_by",
        foreignKey = @ForeignKey(name = "fk_pal_performed_by"))
    private Employee performedBy;

    @Column(name = "details", length = 1000)
    private String details;                     // Human-readable audit message

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "change_snapshot", columnDefinition = "jsonb")
    private String changeSnapshot;              // Full JSON snapshot of entity state
}
