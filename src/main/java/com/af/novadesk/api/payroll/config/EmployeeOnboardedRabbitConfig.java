package com.af.novadesk.api.payroll.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure for employee onboarding events produced by
 * the existing transactional-outbox publisher.
 *
 * <p>Topology:
 * <pre>
 *   EmployeeOutboxPublisher ──► af.employee.events ──► q.employee.onboarded
 *                                                           │
 *                                                (on dead-letter)
 *                                                           ▼
 *                                                af.employee.events.dlx
 *                                                    ──► q.employee.onboarded.dlq
 * </pre>
 */
@Configuration
public class EmployeeOnboardedRabbitConfig {

    @Value("${app.rabbitmq.employee.exchange:af.employee.events}")
    private String exchangeName;

    @Value("${app.rabbitmq.employee.queue:q.employee.onboarded}")
    private String queueName;

    @Value("${app.rabbitmq.employee.routing-key:employee.onboarded.v1}")
    private String routingKey;

    @Value("${app.rabbitmq.employee.dlx:af.employee.events.dlx}")
    private String dlxName;

    @Value("${app.rabbitmq.employee.dlq:q.employee.onboarded.dlq}")
    private String dlqName;

    @Value("${app.rabbitmq.employee.dlq-routing-key:employee.onboarded.dlq}")
    private String dlqRoutingKey;

    // ── Exchange ───────────────────────────────────────────────────────────────

    @Bean
    public TopicExchange employeeEventExchange() {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public DirectExchange employeeEventDlx() {
        return new DirectExchange(dlxName, true, false);
    }

    // ── Queues ─────────────────────────────────────────────────────────────────

    @Bean
    public Queue employeeOnboardedQueue() {
        return QueueBuilder.durable(queueName)
                .ttl(86400000)                           // 24 h message TTL
                .deadLetterExchange(dlxName)
                .deadLetterRoutingKey(dlqRoutingKey)
                .build();
    }

    @Bean
    public Queue employeeOnboardedDlq() {
        return QueueBuilder.durable(dlqName).build();
    }

    // ── Bindings ───────────────────────────────────────────────────────────────

    @Bean
    public Binding employeeOnboardedBinding() {
        return BindingBuilder.bind(employeeOnboardedQueue())
                .to(employeeEventExchange())
                .with(routingKey);
    }

    @Bean
    public Binding employeeOnboardedDlqBinding() {
        return BindingBuilder.bind(employeeOnboardedDlq())
                .to(employeeEventDlx())
                .with(dlqRoutingKey);
    }

    // ── RabbitAdmin (declares exchanges/queues on connect) ─────────────────────

    @Bean
    public RabbitAdmin employeeRabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}
