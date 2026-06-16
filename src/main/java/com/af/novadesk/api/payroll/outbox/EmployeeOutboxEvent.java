package com.af.novadesk.api.payroll.outbox;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.payroll.event.EmployeeEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "employee_outbox_events",
       schema = "af_novadesk_outbox",
       indexes = {
           @Index(name = "idx_emp_outbox_status_created",
                  columnList = "outbox_event_status, created_at"),
           @Index(name = "idx_emp_outbox_aggregate_id",
                  columnList = "aggregate_id"),
           @Index(name = "idx_emp_outbox_org_id",
                  columnList = "organization_id"),
           @Index(name = "idx_emp_outbox_next_retry",
                  columnList = "outbox_event_status, next_retry_at")
       })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmployeeOutboxEvent {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "aggregate_id",
                foreignKey = @ForeignKey(name = "fk_emp_outbox_employee"),
                nullable = false, updatable = false)
    private CmEmployee employee;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private EmployeeEventType eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    private String payload;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "triggered_by_auth_user_id")
    private UUID triggeredByAuthUserId;

    @Column(name = "idempotency_key", nullable = false, unique = true, length = 255)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "outbox_event_status", nullable = false, length = 20)
    private OutboxEventStatus outboxEventStatus;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }
}
