package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.EmployeeOutboxEvent;
import com.af.novadesk.api.payroll.repository.EmployeeOutboxEventRepository;
import com.af.novadesk.api.payroll.service.EmployeeOutboxService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

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
    public EmployeeOutboxEvent createOnboardedEvent(Employee employee, String entityRole, boolean isManager) {
        String eventType = "EMPLOYEE_ONBOARDED";
        String idempotencyKey = eventType + ":" + employee.getId();

        String payload = String.format("""
            {
                "employeeId": "%s",
                "authUserId": "%s",
                "organizationId": "%s",
                "legalEntityId": "%s",
                "employeeCode": "%s",
                "firstName": "%s",
                "lastName": "%s",
                "displayName": "%s",
                "email": "%s",
                "isManager": %b,
                "entityRole": "%s"
            }
            """,
                employee.getId(),
                employee.getAuthUserId(),
                employee.getOrganizationId(),
                employee.getLegalEntity().getId(),
                safeString(employee.getEmployeeCode()),
                safeString(employee.getFirstName()),
                safeString(employee.getLastName()),
                safeString(employee.getFirstName() + " " + employee.getLastName()),
                safeString(employee.getEmail()),
                isManager,
                entityRole
        );

        EmployeeOutboxEvent event = EmployeeOutboxEvent.builder()
                .eventType(eventType)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .organizationId(employee.getOrganizationId())
                .employeeId(employee.getId())
                .authUserId(employee.getAuthUserId())
                .outboxStatus(OutboxEventStatus.PENDING)
                .retryCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        return repository.save(event);
    }

    @Override
    public EmployeeOutboxEvent createRoleChangedEvent(Employee employee, String entityRole, boolean isManager) {
        String eventType = "EMPLOYEE_ROLE_CHANGED";
        String idempotencyKey = eventType + ":" + employee.getId() + ":" + System.currentTimeMillis();

        String payload = String.format("""
            {
                "employeeId": "%s",
                "authUserId": "%s",
                "organizationId": "%s",
                "legalEntityId": "%s",
                "employeeCode": "%s",
                "firstName": "%s",
                "lastName": "%s",
                "displayName": "%s",
                "email": "%s",
                "isManager": %b,
                "entityRole": "%s"
            }
            """,
                employee.getId(),
                employee.getAuthUserId(),
                employee.getOrganizationId(),
                employee.getLegalEntity().getId(),
                safeString(employee.getEmployeeCode()),
                safeString(employee.getFirstName()),
                safeString(employee.getLastName()),
                safeString(employee.getFirstName() + " " + employee.getLastName()),
                safeString(employee.getEmail()),
                isManager,
                entityRole
        );

        EmployeeOutboxEvent event = EmployeeOutboxEvent.builder()
                .eventType(eventType)
                .idempotencyKey(idempotencyKey)
                .payload(payload)
                .organizationId(employee.getOrganizationId())
                .employeeId(employee.getId())
                .authUserId(employee.getAuthUserId())
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
