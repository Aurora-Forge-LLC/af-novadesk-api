package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.payroll.constants.PayrollBatchEventType;
import com.af.novadesk.api.payroll.entity.PayrollBatch;
import com.af.novadesk.api.payroll.entity.PayrollBatchOutboxEvent;
import com.af.novadesk.api.payroll.repository.PayrollBatchOutboxEventRepository;
import com.af.novadesk.api.payroll.service.PayrollBatchOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PayrollBatchOutboxServiceImpl implements PayrollBatchOutboxService {

    private final PayrollBatchOutboxEventRepository repository;

    public PayrollBatchOutboxServiceImpl(PayrollBatchOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public PayrollBatchOutboxEvent createEvent(PayrollBatch payrollBatch,
                                                PayrollBatchEventType eventType,
                                                String payload,
                                                UUID triggeredByAuthUserId) {
        PayrollBatchOutboxEvent event = PayrollBatchOutboxEvent.builder()
                .payrollBatch(payrollBatch)
                .eventType(eventType)
                .payload(payload)
                .organizationId(payrollBatch.getLegalEntity() != null
                        ? payrollBatch.getLegalEntity().getOrganizationId() : null)
                .triggeredByAuthUserId(triggeredByAuthUserId)
                .idempotencyKey(eventType + ":" + payrollBatch.getId() + ":" + UUID.randomUUID())
                .outboxEventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(event);
    }
}
