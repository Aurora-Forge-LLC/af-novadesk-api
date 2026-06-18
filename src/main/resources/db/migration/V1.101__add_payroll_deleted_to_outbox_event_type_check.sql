-- =============================================================================
-- NOVADESK API - Add PAYROLL_DELETED to outbox event type check constraint
-- Version  : 1.93
-- Created  : 2026-06-12
-- Purpose  : The PAYROLL_DELETED event type was added to the
--            PayrollBatchEventType enum and is emitted by
--            PayrollBatchServiceImpl.deletePayrollBatch(), but the DB check
--            constraint chk_pb_outbox_event_type did not include it.
--            This migration alters the constraint to allow PAYROLL_DELETED.
--            (LLR-PAY-02)
-- =============================================================================

SET search_path TO af_novadesk_outbox, af_novadesk;

ALTER TABLE IF EXISTS af_novadesk_outbox.payroll_batch_outbox_events
    DROP CONSTRAINT IF EXISTS chk_pb_outbox_event_type;

ALTER TABLE IF EXISTS af_novadesk_outbox.payroll_batch_outbox_events
    ADD CONSTRAINT chk_pb_outbox_event_type
        CHECK (event_type IN ('PAYROLL_INITIATED','PAYROLL_APPROVED',
                               'PAYROLL_REJECTED','PAYROLL_VOIDED',
                               'PAYROLL_DELETED'));
