-- V1.79: Add leave_policy_id FK to pr_leave_requests.
-- Links each leave request to a configurable leave policy for rule engine validation.

ALTER TABLE af_novadesk.pr_leave_requests
    ADD COLUMN leave_policy_id UUID
        CONSTRAINT fk_lr_leave_policy REFERENCES af_novadesk.pr_leave_policies(id);

CREATE INDEX IF NOT EXISTS idx_lr_policy_id ON af_novadesk.pr_leave_requests(leave_policy_id);
