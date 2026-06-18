-- =============================================================================
-- NOVADESK API - Create employee_outbox_events Table
-- Version  : 1.43
-- Purpose  : Transactional Outbox for the Employee aggregate.
--            Guarantees at-least-once delivery of employee lifecycle events
--            (onboarded, reonboarded, offboarded) to RabbitMQ for AuthHub
--            user provisioning.
-- =============================================================================

SET search_path TO af_novadesk_outbox, af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk_outbox.employee_outbox_events (
    id                         UUID          NOT NULL PRIMARY KEY DEFAULT public.gen_random_uuid(),
    created_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at                 TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    record_status              VARCHAR(20)   NOT NULL DEFAULT 'ACTIVE',
    aggregate_id               UUID          NOT NULL,
    event_type                 VARCHAR(50)   NOT NULL,
    payload                    JSONB         NOT NULL,
    organization_id            UUID          NOT NULL,
    triggered_by_auth_user_id  UUID,
    idempotency_key            VARCHAR(255)  NOT NULL,
    outbox_event_status        VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    published_at               TIMESTAMPTZ,
    retry_count                INTEGER       NOT NULL DEFAULT 0,
    next_retry_at              TIMESTAMPTZ,
    last_error                 VARCHAR(1000),

    CONSTRAINT fk_emp_outbox_employee
        FOREIGN KEY (aggregate_id)
        REFERENCES af_novadesk.cm_employees(id)
        ON DELETE CASCADE,

    CONSTRAINT uk_emp_outbox_idempotency_key
        UNIQUE (idempotency_key),

    CONSTRAINT chk_emp_outbox_event_status
        CHECK (outbox_event_status IN ('PENDING','PUBLISHED','FAILED','DEAD')),
    CONSTRAINT chk_emp_outbox_record_status
        CHECK (record_status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_emp_outbox_event_type
        CHECK (event_type IN ('EMPLOYEE_ONBOARDED','EMPLOYEE_REONBOARDED',
                               'EMPLOYEE_OFFBOARDED','EMPLOYEE_UPDATED')),
    CONSTRAINT chk_emp_outbox_retry_count
        CHECK (retry_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_emp_outbox_status_created
    ON af_novadesk_outbox.employee_outbox_events (outbox_event_status, created_at);

CREATE INDEX IF NOT EXISTS idx_emp_outbox_aggregate_id
    ON af_novadesk_outbox.employee_outbox_events (aggregate_id);

CREATE INDEX IF NOT EXISTS idx_emp_outbox_org_id
    ON af_novadesk_outbox.employee_outbox_events (organization_id);

CREATE INDEX IF NOT EXISTS idx_emp_outbox_next_retry
    ON af_novadesk_outbox.employee_outbox_events (outbox_event_status, next_retry_at)
    WHERE outbox_event_status IN ('PENDING', 'FAILED');

COMMENT ON TABLE af_novadesk_outbox.employee_outbox_events IS
    'Transactional Outbox for the Employee aggregate — publishes lifecycle events to RabbitMQ for AuthHub user provisioning.';
