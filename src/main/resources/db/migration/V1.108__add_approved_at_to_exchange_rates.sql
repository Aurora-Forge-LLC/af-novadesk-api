ALTER TABLE af_novadesk.fa_exchange_rates
    ADD COLUMN IF NOT EXISTS approved_at TIMESTAMPTZ;
