package com.af.novadesk.api.finance.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes entity-access-granted email events directly to RabbitMQ.
 *
 * <p>These events are consumed by {@code af-notification} (af-authhub-mailer)
 * which renders the FreeMarker template and sends the email via Zoho SMTP.</p>
 *
 * <p>This publisher sends to the same {@code email-events-exchange} that
 * AuthHub's {@code EmailEventListener} uses, with the same routing key
 * {@code email.send.event}. The mailer consumer treats both sources identically.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EntityAccessEmailPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final AtomicLong sequenceCounter = new AtomicLong(0);

    @Value("${app.rabbitmq.email.exchange:email-events-exchange}")
    private String emailExchangeName;

    @Value("${app.rabbitmq.email.routing-key:email.send.event}")
    private String emailRoutingKey;

    /**
     * Publishes an entity-access-granted email event to RabbitMQ.
     *
     * @param recipient  the user's email address
     * @param firstName  the user's first name (for personalisation)
     * @param entityName the legal entity name
     * @param orgName    the organisation name
     * @param role       the entity-level role granted (e.g., FINANCE_MANAGER)
     * @param loginLink  URL to log in and access the entity
     */
    public void publishEntityAccessGrantedEmail(String recipient,
                                                 String firstName,
                                                 String entityName,
                                                 String orgName,
                                                 String role,
                                                 String loginLink) {
        try {
            var emailRequest = new EmailSentEvent.EmailSendRequest();
            emailRequest.setRecipient(recipient);
            emailRequest.setTemplateType("ENTITY_ACCESS_GRANTED");
            emailRequest.setVariables(Map.of(
                    "firstName", firstName != null ? firstName : recipient,
                    "entityName", entityName,
                    "orgName", orgName,
                    "role", role,
                    "loginLink", loginLink
            ));

            var event = new EmailSentEvent();
            event.setEventId(UUID.randomUUID());
            event.setSequenceNumber(sequenceCounter.incrementAndGet());
            event.setEmailRequest(emailRequest);
            event.setTimestamp(LocalDateTime.now());

            rabbitTemplate.convertAndSend(emailExchangeName, emailRoutingKey, event);
            log.info("Entity access granted email published to {} for {} role {}",
                    recipient, entityName, role);
        } catch (Exception e) {
            // Log but do not fail — email delivery is best-effort.
            // The outbox pattern (EntityUserAccessOutboxService) handles
            // guaranteed delivery of the access grant itself.
            log.error("Failed to publish entity access granted email to {}: {}",
                    recipient, e.getMessage());
        }
    }

    /**
     * Minimal DTO matching the structure expected by af-notification's EmailSentEvent.
     * Must stay in sync with af-authhub's EmailSentEvent and EmailSendRequest.
     */
    private static class EmailSentEvent {
        private UUID eventId;
        private long sequenceNumber;
        private EmailSendRequest emailRequest;
        private LocalDateTime timestamp;

        public UUID getEventId() { return eventId; }
        public void setEventId(UUID eventId) { this.eventId = eventId; }
        public long getSequenceNumber() { return sequenceNumber; }
        public void setSequenceNumber(long sequenceNumber) { this.sequenceNumber = sequenceNumber; }
        public EmailSendRequest getEmailRequest() { return emailRequest; }
        public void setEmailRequest(EmailSendRequest emailRequest) { this.emailRequest = emailRequest; }
        public LocalDateTime getTimestamp() { return timestamp; }
        public void setTimestamp(LocalDateTime timestamp) { this.timestamp = timestamp; }

        public static class EmailSendRequest {
            private String recipient;
            private String templateType;
            private java.util.Map<String, String> variables;

            public String getRecipient() { return recipient; }
            public void setRecipient(String recipient) { this.recipient = recipient; }
            public String getTemplateType() { return templateType; }
            public void setTemplateType(String templateType) { this.templateType = templateType; }
            public java.util.Map<String, String> getVariables() { return variables; }
            public void setVariables(java.util.Map<String, String> variables) { this.variables = variables; }
        }
    }
}
