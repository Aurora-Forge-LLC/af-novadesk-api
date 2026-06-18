package com.af.novadesk.api.payroll.event;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes {@code USER_CREATED} events published by AuthHub after
 * successful user provisioning, confirming that the AuthHub identity
 * was created and the invite email was sent.
 *
 * <p>This completes the async feedback loop:
 * NovaDesk onboard → outbox → RabbitMQ → AuthHub creates user + invite
 * → AuthHub publishes USER_CREATED → NovaDesk confirms employee status.</p>
 *
 * <p>The employee is already in {@code PENDING_SETUP} status from onboarding;
 * this handler serves as a confirmation that AuthHub provisioning succeeded.
 * The auto-transition to {@code ACTIVE} happens on first login via
 * {@code EmployeeServiceImpl.getCurrentEmployee()}.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeEventHandler {

    private final CmEmployeeRepository cmEmployeeRepository;
    private final ObjectMapper objectMapper;

    private static final String QUEUE_USER_CREATED = "novadesk.employee.user-created";

    @RabbitListener(queues = QUEUE_USER_CREATED)
    public void onUserCreated(String payload) {
        try {
            JsonNode json = objectMapper.readTree(payload);
            UUID userId = UUID.fromString(json.get("user_id").asText());
            UUID organizationId = UUID.fromString(json.get("organization_id").asText());

            cmEmployeeRepository.findByAuthUserIdAndOrganizationId(userId, organizationId)
                    .ifPresentOrElse(
                            cm -> {
                                if (cm.getEmployeeStatus() == EmployeeStatus.PENDING_SETUP) {
                                    // Already in PENDING_SETUP — AuthHub user created,
                                    // invite email sent. Status auto-transitions to ACTIVE
                                    // on first login via getCurrentEmployee().
                                    log.info("AuthHub user confirmed for employee: employeeId={}, authUserId={}",
                                            cm.getId(), userId);
                                }
                            },
                            () -> log.warn("USER_CREATED event for unknown employee: userId={}, orgId={}",
                                    userId, organizationId)
                    );
        } catch (Exception e) {
            log.error("Failed to process USER_CREATED event: {}", e.getMessage(), e);
        }
    }
}
