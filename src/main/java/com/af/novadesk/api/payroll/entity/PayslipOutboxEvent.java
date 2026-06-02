package com.af.novadesk.api.payroll.entity;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.AbstractEntity;
import com.af.novadesk.api.payroll.constants.PayslipEventType;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Transactional Outbox for the {@link Payslip} aggregate (LLR-PAY-02.5).
 *
 * <p>Fired after a payslip PDF is generated to trigger employee notification
 * and self-service portal refresh.</p>
 *
 * <p>Idempotency key convention:
 * {@code "<PayslipEventType>:<payslipId>:<requestTraceId>"}</p>
 */
@Entity
@Table(name = "payslip_outbox_events", schema = "af_novadesk_outbox",
    indexes = {
        @Index(columnList = "outbox_event_status, created_at", name = "idx_ps_outbox_status_created"),
        @Index(columnList = "aggregate_id", name = "idx_ps_outbox_aggregate_id"),
        @Index(columnList = "organization_id", name = "idx_ps_outbox_org_id")
    },
    uniqueConstraints = {
        @UniqueConstraint(columnNames = "idempotency_key", name = "uk_ps_outbox_idempotency_key")
    })
@AttributeOverride(name = "status",
    column = @Column(name = "record_status", nullable = false, length = 20))
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(exclude = {"payslip"})
public class PayslipOutboxEvent extends AbstractEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "aggregate_id", nullable = false,
        foreignKey = @ForeignKey(name = "fk_ps_outbox_payslip"))
    @NotNull(message = "Payslip is required")
    private Payslip payslip;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    @NotNull(message = "Event type is required")
    private PayslipEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    @NotBlank(message = "Payload is required")
    private String payload;

    @Column(name = "organization_id", nullable = false)
    @NotNull
    private UUID organizationId;

    @Column(name = "triggered_by_auth_user_id")
    private UUID triggeredByAuthUserId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbox_event_status", nullable = false, length = 20)
    @Builder.Default
    @NotNull
    private OutboxEventStatus outboxEventStatus = OutboxEventStatus.PENDING;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    @NotNull
    private Integer retryCount = 0;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}
