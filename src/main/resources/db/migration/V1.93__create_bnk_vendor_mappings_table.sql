-- LLR-BNK-02.5: Create bnk_vendor_mappings table for learned vendor name patterns
CREATE TABLE IF NOT EXISTS af_novadesk.bnk_vendor_mappings (
    id                      UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    legal_entity_id         UUID         NOT NULL REFERENCES af_novadesk.legal_entities(id) ON DELETE CASCADE,
    bank_description_pattern VARCHAR(255) NOT NULL,         -- e.g., "AMZN%", "AWS*%"
    vendor_id               UUID         NOT NULL REFERENCES af_novadesk.exp_vendors(id) ON DELETE CASCADE,
    confidence_level        VARCHAR(20)  NOT NULL DEFAULT 'USER_CONFIRMED',  -- USER_CONFIRMED, AUTO_LEARNED
    match_count             INTEGER      NOT NULL DEFAULT 1,
    created_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at              TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status                  VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_bnk_vm_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_vm_confidence CHECK (confidence_level IN ('USER_CONFIRMED','AUTO_LEARNED')),
    CONSTRAINT uq_bnk_vendor_mapping UNIQUE (legal_entity_id, bank_description_pattern)
);

CREATE INDEX IF NOT EXISTS idx_bnk_vm_entity ON af_novadesk.bnk_vendor_mappings (legal_entity_id);
CREATE INDEX IF NOT EXISTS idx_bnk_vm_vendor ON af_novadesk.bnk_vendor_mappings (vendor_id);

COMMENT ON TABLE af_novadesk.bnk_vendor_mappings IS 'Learned vendor name patterns from bank statement descriptions';
COMMENT ON COLUMN af_novadesk.bnk_vendor_mappings.bank_description_pattern IS 'SQL LIKE pattern, e.g. AMZN% or %AWS%';
