package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.payroll.constants.PayslipEventType;
import com.af.novadesk.api.payroll.entity.Payslip;
import com.af.novadesk.api.payroll.entity.PayslipOutboxEvent;
import com.af.novadesk.api.payroll.repository.PayslipOutboxEventRepository;
import com.af.novadesk.api.payroll.service.PayslipOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class PayslipOutboxServiceImpl implements PayslipOutboxService {

    private final PayslipOutboxEventRepository repository;

    public PayslipOutboxServiceImpl(PayslipOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public PayslipOutboxEvent createEvent(Payslip payslip,
                                           PayslipEventType eventType,
                                           String payload,
                                           UUID triggeredByAuthUserId) {
        PayslipOutboxEvent event = PayslipOutboxEvent.builder()
                .payslip(payslip)
                .eventType(eventType)
                .payload(payload)
                .organizationId(payslip.getOrganizationId())
                .triggeredByAuthUserId(triggeredByAuthUserId)
                .idempotencyKey(eventType + ":" + payslip.getId() + ":" + UUID.randomUUID())
                .outboxEventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(event);
    }
}
