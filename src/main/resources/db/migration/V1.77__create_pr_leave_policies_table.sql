-- V1.77: Create pr_leave_policies table for configurable per-entity leave policy rule engine.
-- Each entity can define multiple named leave types (e.g. Personal, Sick, PTO, Maternity)
-- with per-policy rules for payment type, allocation, earning schedule, and borrowing.

CREATE TABLE af_novadesk.pr_leave_policies (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id UUID NOT NULL
        CONSTRAINT fk_lp_legal_entity REFERENCES af_novadesk.legal_entities(id),

    -- Policy definition
    name            VARCHAR(100) NOT NULL,        -- e.g. "Personal", "Sick", "PTO", "Maternity"
    payment_type    VARCHAR(10) NOT NULL           -- 'PAID' or 'UNPAID'
        CHECK (payment_type IN ('PAID', 'UNPAID')),

    -- Allocation
    allowed_days    INTEGER NOT NULL,             -- yearly allocation; 0 when is_unlimited = true
    is_unlimited    BOOLEAN NOT NULL DEFAULT FALSE,

    -- Earned leave rules
    is_earned       BOOLEAN NOT NULL DEFAULT FALSE,
    -- If is_earned = true, monthly accrual = allowed_days / 12
    -- If is_earned = false, all days available upfront

    -- Borrowing rules (only applies when is_earned = true)
    borrow_multiple INTEGER NULL DEFAULT NULL,    -- e.g. 2 = can borrow up to 2x earned days

    -- Status (soft-delete support)
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE'
        CHECK (status IN ('ACTIVE', 'INACTIVE')),

    -- Audit
    created_by      UUID NOT NULL,                -- authUserId of creator
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- One policy per name per entity
    CONSTRAINT uk_lp_entity_name UNIQUE (legal_entity_id, name)
);

CREATE INDEX idx_lp_entity_id ON af_novadesk.pr_leave_policies(legal_entity_id);
CREATE INDEX idx_lp_entity_status ON af_novadesk.pr_leave_policies(legal_entity_id, status);
