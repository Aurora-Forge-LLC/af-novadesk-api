package com.af.novadesk.api.payroll.event;

import com.af.novadesk.api.payroll.service.EmployeeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;

/**
 * RabbitMQ consumer for USER_PASSWORD_SET events published by af-authhub.
 *
 * <p>When an employee completes their password setup in AuthHub, this listener
 * receives the event and transitions the corresponding CmEmployee status
 * from PENDING_SETUP to ACTIVE.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EmployeeActivationEventListener {

    private final EmployeeService employeeService;
    private final ObjectMapper objectMapper;

    /**
     * Queue: {@code q.employee.activated} (bound to {@code af.employee.events}
     * with routing key {@code employee.password.set.v1}).
     *
     * <p>Uses raw {@link Message} to avoid __TypeId__ deserialization conflicts
     * between af-authhub's and af-novadesk-api's UserPasswordSetEvent classes.
     */
    @RabbitListener(queues = "${app.rabbitmq.employee.activation-queue:q.employee.activated}")
    public void onUserPasswordSet(Message message) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = objectMapper.readValue(message.getBody(), Map.class);

            String authUserIdStr = (String) payload.get("auth_user_id");
            String orgIdStr = (String) payload.get("organization_id");

            if (authUserIdStr == null || orgIdStr == null) {
                log.warn("Invalid password-set event payload: missing auth_user_id or organization_id");
                return;
            }

            UUID authUserId = UUID.fromString(authUserIdStr);
            UUID orgId = UUID.fromString(orgIdStr);

            log.info("Received USER_PASSWORD_SET event: authUserId={}, orgId={}", authUserId, orgId);

            employeeService.activateEmployee(authUserId, orgId);
            log.info("Employee activated successfully: authUserId={}", authUserId);

        } catch (IOException e) {
            log.error("Failed to deserialize password-set event: {}", e.getMessage(), e);
        } catch (IllegalArgumentException e) {
            log.error("Invalid UUID in password-set event: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Failed to activate employee: {}", e.getMessage(), e);
        }
    }
}
