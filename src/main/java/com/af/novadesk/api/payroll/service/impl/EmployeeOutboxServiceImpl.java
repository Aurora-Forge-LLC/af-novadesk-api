package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.EmployeeOutboxEvent;
import com.af.novadesk.api.common.repository.EmployeeOutboxEventRepository;
import com.af.novadesk.api.payroll.service.EmployeeOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Default implementation of {@link EmployeeOutboxService}.
 *
 * <p>Persists events in the same transaction as the Employee CRUD operation.
 * The payload JSON structure is shared with cross-module consumers.</p>
 */
@Service
@Transactional
public class EmployeeOutboxServiceImpl implements EmployeeOutboxService {

    private final EmployeeOutboxEventRepository repository;

    public EmployeeOutboxServiceImpl(EmployeeOutboxEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public EmployeeOutboxEvent createOnboardedEvent(CmEmployee cmEmployee, CmEmployeeEntityAssignment assignment, String entityRole, boolean isManager) {
        String eventType = "EMPLOYEE_ONBOARDED";
        String idempotencyKey = eventType + ":" + cmEmployee.getId();

        String payload = String.format("""
            {
                "employeeId": "%s",
                "authUserId": "%s",
                "organizationId": "%s",
                "legalEntityId": "%s",
                "employeeCode": "%s",
                "displayName": "%s",
                "email": "%s",
                "isManager": %b,
                "entityRole": "%s"
            }
            """,
                cmEmployee.getId(),
                cmEmployee.getAuthUserId(),
                cmEmployee.getOrganizationId(),
                assignment.getLegalEntity().getId(),
                safeString(cmEmployee.getEmployeeCode()),
                safeString(cmEmployee.getDisplayName()),
                safeString(cmEmployee.getEmail()),
                isManager,
                entityRole
        );

        EmployeeOutboxEvent event = EmployeeOutboxEvent.builder()
                .eventType(eventType)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .organizationId(cmEmployee.getOrganizationId())
                .employeeId(cmEmployee.getId())
                .authUserId(cmEmployee.getAuthUserId())
                .outboxStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return repository.save(event);
    }

    @Override
    public EmployeeOutboxEvent createRoleChangedEvent(CmEmployee cmEmployee, CmEmployeeEntityAssignment assignment, String entityRole, boolean isManager) {
        String eventType = "EMPLOYEE_ROLE_CHANGED";
        String idempotencyKey = eventType + ":" + cmEmployee.getId() + ":" + System.currentTimeMillis();

        String payload = String.format("""
            {
                "employeeId": "%s",
                "authUserId": "%s",
                "organizationId": "%s",
                "legalEntityId": "%s",
                "employeeCode": "%s",
                "displayName": "%s",
                "email": "%s",
                "isManager": %b,
                "entityRole": "%s"
            }
            """,
                cmEmployee.getId(),
                cmEmployee.getAuthUserId(),
                cmEmployee.getOrganizationId(),
                assignment.getLegalEntity().getId(),
                safeString(cmEmployee.getEmployeeCode()),
                safeString(cmEmployee.getDisplayName()),
                safeString(cmEmployee.getEmail()),
                isManager,
                entityRole
        );

        EmployeeOutboxEvent event = EmployeeOutboxEvent.builder()
                .eventType(eventType)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .organizationId(cmEmployee.getOrganizationId())
                .employeeId(cmEmployee.getId())
                .authUserId(cmEmployee.getAuthUserId())
                .outboxStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return repository.save(event);
    }

    private String safeString(String value) {
        return value != null ? value.replace("\"", "\\\"") : "";
    }
}
