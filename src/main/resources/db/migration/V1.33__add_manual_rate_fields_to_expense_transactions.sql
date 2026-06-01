-- V1.33 — Store manual exchange rate audit trail on expense transactions
--
-- When the system cannot resolve an exchange rate automatically (HTTP 422 / FIN_RATE_002),
-- the caller may supply a manual rate together with a justification and approver name.
-- These three columns preserve that audit trail directly on the transaction record.
-- All three are nullable — they are only populated when a manual rate was used.

ALTER TABLE af_novadesk.exp_expense_transactions
    ADD COLUMN IF NOT EXISTS manual_exchange_rate      NUMERIC(19, 6),
    ADD COLUMN IF NOT EXISTS manual_rate_justification VARCHAR(500),
    ADD COLUMN IF NOT EXISTS manual_rate_approved_by   VARCHAR(150);

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.manual_exchange_rate
    IS 'Manual exchange rate (source → USD) supplied by the user when no system rate was available. Null when rate was resolved automatically.';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.manual_rate_justification
    IS 'Mandatory audit justification for why a manual rate was needed. Present only when manual_exchange_rate is non-null.';

COMMENT ON COLUMN af_novadesk.exp_expense_transactions.manual_rate_approved_by
    IS 'Name or email of the approver who authorised the manual rate. Present only when manual_exchange_rate is non-null.';
