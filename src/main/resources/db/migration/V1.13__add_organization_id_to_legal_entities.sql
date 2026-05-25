-- Add organization_id column to legal_entities table
ALTER TABLE IF EXISTS af_novadesk.legal_entities
    ADD COLUMN IF NOT EXISTS organization_id UUID;
