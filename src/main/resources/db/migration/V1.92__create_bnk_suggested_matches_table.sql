-- LLR-BNK-02.4: Create bnk_suggested_matches table for medium-confidence match suggestions
CREATE TABLE IF NOT EXISTS af_novadesk.bnk_suggested_matches (
    id                    UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    bank_transaction_id   UUID         NOT NULL REFERENCES af_novadesk.bnk_transactions(id) ON DELETE CASCADE,
    expense_transaction_id UUID        NOT NULL REFERENCES af_novadesk.exp_expense_transactions(id) ON DELETE CASCADE,
    matching_score        INTEGER      NOT NULL,              -- 60-79 range
    score_breakdown       JSONB,                              -- {"amount":50,"date":20,"description":5}
    suggested_by          VARCHAR(20)  NOT NULL DEFAULT 'SYSTEM',  -- SYSTEM, USER
    suggestion_status     VARCHAR(20)  NOT NULL DEFAULT 'PENDING', -- PENDING, ACCEPTED, REJECTED
    resolved_by_shadow_user_id UUID,
    resolved_at           TIMESTAMPTZ,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    status                VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE',

    CONSTRAINT chk_bnk_suggest_status CHECK (status IN ('ACTIVE','INACTIVE','SUSPENDED','DELETED')),
    CONSTRAINT chk_bnk_suggest_suggestion_status CHECK (suggestion_status IN ('PENDING','ACCEPTED','REJECTED')),
    CONSTRAINT chk_bnk_suggest_suggested_by CHECK (suggested_by IN ('SYSTEM','USER')),
    CONSTRAINT uq_bnk_suggest_pair UNIQUE (bank_transaction_id, expense_transaction_id)
);

CREATE INDEX IF NOT EXISTS idx_bnk_suggest_bank_txn ON af_novadesk.bnk_suggested_matches (bank_transaction_id);
CREATE INDEX IF NOT EXISTS idx_bnk_suggest_status ON af_novadesk.bnk_suggested_matches (suggestion_status);
CREATE INDEX IF NOT EXISTS idx_bnk_suggest_expense ON af_novadesk.bnk_suggested_matches (expense_transaction_id);

COMMENT ON TABLE af_novadesk.bnk_suggested_matches IS 'Medium-confidence match suggestions for user review';
COMMENT ON COLUMN af_novadesk.bnk_suggested_matches.score_breakdown IS 'JSON breakdown: {"amount":50,"date":20,"description":5}';
