-- =============================================================================
-- NOVADESK API - Create pr_employees Table
-- Version  : 1.34
-- Created  : 2026-06-01
-- Purpose  : Payroll-specific employee records linked to ShadowUser identities.
--            Every Employee references a ShadowUser (FK to identity module) and
--            adds entity assignment, compensation, and bank details.
--            All Employees are ShadowUsers, but not all ShadowUsers are Employees.
--            (LLR-PAY-01.1)
-- =============================================================================

SET search_path TO af_novadesk;

CREATE TABLE IF NOT EXISTS af_novadesk.pr_employees (
    id                    UUID           PRIMARY KEY DEFAULT public.gen_random_uuid(),
    shadow_user_id        UUID           NOT NULL REFERENCES af_novadesk.shadow_users(id)       ON DELETE RESTRICT,
    organization_id       UUID           NOT NULL,
    auth_user_id          UUID           NOT NULL,
    legal_entity_id       UUID           NOT NULL REFERENCES af_novadesk.legal_entities(id)     ON DELETE RESTRICT,
    employee_code         VARCHAR(50)    NOT NULL,
    first_name            VARCHAR(100)   NOT NULL,
    last_name             VARCHAR(100)   NOT NULL,
    email                 VARCHAR(255),
    department            VARCHAR(100),
    designation           VARCHAR(100),
    hire_date             DATE           NOT NULL,
    termination_date      DATE,
    manager_id            UUID           REFERENCES af_novadesk.pr_employees(id)                ON DELETE SET NULL,
    base_salary           DECIMAL(19,4)  NOT NULL CHECK (base_salary >= 0),
    salary_currency       CHAR(3)        NOT NULL,
    bank_account_number   VARCHAR(50),
    bank_name             VARCHAR(150),
    bank_ifsc_code        VARCHAR(20),
    status                VARCHAR(20)    NOT NULL DEFAULT 'ACTIVE',
    created_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ    NOT NULL DEFAULT NOW(),

    CONSTRAINT uk_emp_user_entity
        UNIQUE (shadow_user_id, legal_entity_id),
    CONSTRAINT uk_emp_code_entity
        UNIQUE (employee_code, legal_entity_id),
    CONSTRAINT chk_emp_status
        CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_emp_termination_date
        CHECK (termination_date IS NULL OR termination_date >= hire_date),
    CONSTRAINT chk_emp_salary_currency
        CHECK (salary_currency ~ '^[A-Z]{3}$')
);

CREATE INDEX IF NOT EXISTS idx_emp_entity_status
    ON af_novadesk.pr_employees (legal_entity_id, status);

CREATE INDEX IF NOT EXISTS idx_emp_org_id
    ON af_novadesk.pr_employees (organization_id);

CREATE INDEX IF NOT EXISTS idx_emp_shadow_user
    ON af_novadesk.pr_employees (shadow_user_id);

COMMENT ON TABLE af_novadesk.pr_employees IS
    'Payroll-specific employee records linked to ShadowUser identities (LLR-PAY-01.1). All Employees are ShadowUsers, but not vice versa.';

COMMENT ON COLUMN af_novadesk.pr_employees.shadow_user_id IS
    'FK to shadow_users.id — the AuthHub identity cache from the identity module.';

COMMENT ON COLUMN af_novadesk.pr_employees.organization_id IS
    'Denormalized from ShadowUser for direct @Filter usage without JOIN.';

COMMENT ON COLUMN af_novadesk.pr_employees.manager_id IS
    'Self-referential FK for the manager hierarchy (LLR-PAY-01.3).';
