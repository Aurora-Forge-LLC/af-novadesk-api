-- V1.94: Add optimistic locking version column to bnk_transactions
-- Prevents concurrent categorization of the same bank transaction (LLR-BNK-03 Technical Notes).

ALTER TABLE af_novadesk.bnk_transactions
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
