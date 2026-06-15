-- V1.94: Relax leave policy name uniqueness to only ACTIVE policies.
-- Soft-deleted (INACTIVE) policies should not block creation of a new
-- policy with the same name.  Replaces the hard UNIQUE constraint with
-- a partial unique index that only applies to ACTIVE rows.
SET search_path TO af_novadesk;

-- Drop the absolute uniqueness constraint
ALTER TABLE af_novadesk.pr_leave_policies DROP CONSTRAINT IF EXISTS uk_lp_entity_name;

-- Create a partial unique index that only enforces uniqueness among ACTIVE policies
CREATE UNIQUE INDEX IF NOT EXISTS uk_lp_entity_name_active
    ON af_novadesk.pr_leave_policies (legal_entity_id, name)
    WHERE status = 'ACTIVE';
