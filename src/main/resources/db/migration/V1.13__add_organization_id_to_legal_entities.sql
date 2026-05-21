-- Add organization_id column to legal_entities table
ALTER TABLE  af_novadesk.legal_entities
    ADD COLUMN organization_id UUID;