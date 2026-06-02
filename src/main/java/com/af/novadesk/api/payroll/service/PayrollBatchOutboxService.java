package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.constants.PayrollBatchEventType;
import com.af.novadesk.api.payroll.entity.PayrollBatch;
import com.af.novadesk.api.payroll.entity.PayrollBatchOutboxEvent;

import java.util.UUID;

/**
 * Outbox service for PayrollBatch aggregate events.
 */
public interface PayrollBatchOutboxService {
    PayrollBatchOutboxEvent createEvent(PayrollBatch payrollBatch,
                                         PayrollBatchEventType eventType,
                                         String payload,
                                         UUID triggeredByAuthUserId);
}
