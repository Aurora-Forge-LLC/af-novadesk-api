package com.af.novadesk.api.payroll.outbox;

import com.af.novadesk.api.common.constants.OutboxEventStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.exception.OutboxPublishException;
import com.af.novadesk.api.payroll.event.EmployeeEventPayload;
import com.af.novadesk.api.payroll.event.EmployeeEventType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Writes employee lifecycle events to the transactional outbox
 * in the same database transaction as the employee mutation.
 *
 * <p>The {@link EmployeeOutboxPublisher} picks up PENDING rows
 * and publishes them to RabbitMQ for AuthHub consumption.</p>
 */
@Slf4j
@Service
public class EmployeeOutboxService {

    private final EmployeeOutboxEventRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public EmployeeOutboxService(EmployeeOutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    }

    /**
     * Writes an EMPLOYEE_ONBOARDED outbox event for AuthHub user provisioning.
     */
    public void publishEmployeeOnboarded(CmEmployee employee, UUID userId,
                                         String email, String firstName, String lastName,
                                         UUID orgId, UUID triggeredBy) {
        EmployeeEventPayload payload = EmployeeEventPayload.builder()
                .userId(userId)
                .employeeId(employee.getId())
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .organizationId(orgId)
                .build();
        persist(employee, EmployeeEventType.EMPLOYEE_ONBOARDED,
                triggeredBy, orgId, payload);
    }

    /**
     * Writes an EMPLOYEE_OFFBOARDED outbox event for AuthHub user deactivation.
     */
    public void publishEmployeeOffboarded(CmEmployee employee,
                                          UUID orgId, UUID triggeredBy) {
        EmployeeEventPayload payload = EmployeeEventPayload.builder()
                .userId(employee.getAuthUserId())
                .employeeId(employee.getId())
                .email(employee.getEmail())
                .organizationId(orgId)
                .build();
        persist(employee, EmployeeEventType.EMPLOYEE_OFFBOARDED,
                triggeredBy, orgId, payload);
    }

    /**
     * Writes an EMPLOYEE_REONBOARDED outbox event for AuthHub user reactivation.
     */
    public void publishEmployeeReonboarded(CmEmployee employee, UUID userId,
                                           String email, String firstName, String lastName,
                                           UUID orgId, UUID triggeredBy) {
        EmployeeEventPayload payload = EmployeeEventPayload.builder()
                .userId(userId)
                .employeeId(employee.getId())
                .email(email)
                .firstName(firstName)
                .lastName(lastName)
                .organizationId(orgId)
                .build();
        persist(employee, EmployeeEventType.EMPLOYEE_REONBOARDED,
                triggeredBy, orgId, payload);
    }

    private void persist(CmEmployee employee, EmployeeEventType eventType,
                         UUID triggeredBy, UUID orgId,
                         EmployeeEventPayload payload) {
        try {
            String idempotencyKey = eventType.name() + ":"
                    + employee.getId() + ":" + UUID.randomUUID();
            String payloadJson = objectMapper.writeValueAsString(payload);

            EmployeeOutboxEvent event = EmployeeOutboxEvent.builder()
                    .employee(employee)
                    .eventType(eventType)
                    .payload(payloadJson)
                    .organizationId(orgId)
                    .triggeredByAuthUserId(triggeredBy)
                    .idempotencyKey(idempotencyKey)
                    .outboxEventStatus(OutboxEventStatus.PENDING)
                    .retryCount(0)
                    .build();
            outboxRepository.save(event);
            log.debug("Employee outbox event persisted: type={}, employeeId={}",
                    eventType, employee.getId());
        } catch (JsonProcessingException e) {
            throw new OutboxPublishException(
                    eventType.name(), employee.getId(), e);
        }
    }
}
