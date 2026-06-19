package com.af.novadesk.api.payroll.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ infrastructure for consuming employee activation events
 * published by af-authhub when an employee completes password setup.
 *
 * <p>Topology:
 * <pre>
 *   af-authhub ──► af.employee.events ──► q.employee.activated
 *                     (routing key: employee.password.set.v1)
 * </pre>
 */
@Configuration
public class EmployeeActivationRabbitConfig {

    @Value("${app.rabbitmq.employee.exchange:af.employee.events}")
    private String exchangeName;

    @Value("${app.rabbitmq.employee.activation-queue:q.employee.activated}")
    private String activationQueueName;

    @Value("${app.rabbitmq.employee.activation-routing-key:employee.password.set.v1}")
    private String activationRoutingKey;

    // ── Exchange (reuses af.employee.events topic exchange) ────────────────

    /**
     * The queue that receives USER_PASSWORD_SET events from af-authhub.
     */
    @Bean
    public Queue employeeActivationQueue() {
        return QueueBuilder.durable(activationQueueName).build();
    }

    /**
     * Binds the activation queue to the employee events topic exchange
     * with routing key {@code employee.password.set.v1}.
     */
    @Bean
    public Binding employeeActivationBinding() {
        return BindingBuilder.bind(employeeActivationQueue())
                .to(new TopicExchange(exchangeName, true, false))
                .with(activationRoutingKey);
    }

    @Bean
    public RabbitAdmin employeeActivationRabbitAdmin(ConnectionFactory connectionFactory) {
        RabbitAdmin admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(true);
        return admin;
    }
}
