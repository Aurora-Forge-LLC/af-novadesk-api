ALTER TABLE af_novadesk.fa_exchange_rates
    ADD COLUMN IF NOT EXISTS approval_status VARCHAR(20) NOT NULL DEFAULT 'APPROVAL_PENDING'
        CHECK (approval_status IN ('APPROVAL_PENDING', 'APPROVED', 'REJECTED'));
