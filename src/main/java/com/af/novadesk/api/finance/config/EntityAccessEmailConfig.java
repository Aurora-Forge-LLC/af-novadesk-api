package com.af.novadesk.api.finance.config;

import org.springframework.amqp.core.TopicExchange;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ configuration for publishing entity-access-granted email events.
 *
 * <p>Declares the same {@code email-events-exchange} that af-authhub's mailer
 * consumer listens to. NovaDesk publishes {@code EmailSentEvent} messages to
 * this exchange, and {@code af-notification} (af-authhub-mailer) consumes them
 * from the {@code email-queue} bound to this exchange.</p>
 *
 * <p>This avoids coupling NovaDesk to AuthHub's internal email API — both
 * services simply agree on the exchange name and message format.</p>
 */
@Configuration
public class EntityAccessEmailConfig {

    @Value("${app.rabbitmq.email.exchange:email-events-exchange}")
    private String emailExchangeName;

    /**
     * The topic exchange used by the entire AuroraForge email delivery pipeline.
     * AuthHub publishes here, NovaDesk publishes here, af-notification consumes from here.
     */
    @Bean
    public TopicExchange emailEventsExchange() {
        return new TopicExchange(emailExchangeName, true, false);
    }
}
