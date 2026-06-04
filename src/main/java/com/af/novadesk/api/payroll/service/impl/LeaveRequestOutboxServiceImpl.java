package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.payroll.constants.LeaveRequestEventType;
import com.af.novadesk.api.payroll.entity.LeaveRequest;
import com.af.novadesk.api.payroll.entity.LeaveRequestOutboxEvent;
import com.af.novadesk.api.payroll.repository.LeaveRequestOutboxEventRepository;
import com.af.novadesk.api.payroll.service.LeaveRequestOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Transactional
public class LeaveRequestOutboxServiceImpl implements LeaveRequestOutboxService {

    private final LeaveRequestOutboxEventRepository repository;

    public LeaveRequestOutboxServiceImpl(LeaveRequestOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public LeaveRequestOutboxEvent createEvent(LeaveRequest leaveRequest,
                                                LeaveRequestEventType eventType,
                                                String payload,
                                                UUID triggeredByAuthUserId) {
        LeaveRequestOutboxEvent event = LeaveRequestOutboxEvent.builder()
                .leaveRequest(leaveRequest)
                .eventType(eventType)
                .payload(payload)
                .organizationId(leaveRequest.getEmployee() != null
                        ? leaveRequest.getEmployee().getOrganizationId() : null)
                .triggeredByAuthUserId(triggeredByAuthUserId)
                .idempotencyKey(eventType + ":" + leaveRequest.getId() + ":" + UUID.randomUUID())
                .outboxEventStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
        return repository.save(event);
    }
}
