package com.af.novadesk.api.payroll.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for employee lifecycle events.
 *
 * <p>NovaDesk publishes employee events to the {@code employee-events}
 * topic exchange. AuthHub binds its consumer queue(s) to the relevant
 * routing keys.</p>
 */
@Configuration
public class EmployeeRabbitMqConfig {

    public static final String EXCHANGE = "employee-events";

    public static final String QUEUE_AUTHHUB_ONBOARDED = "authhub.employee.onboarded";
    public static final String QUEUE_AUTHHUB_OFFBOARDED = "authhub.employee.offboarded";

    public static final String RK_ONBOARDED  = "employee.onboarded";
    public static final String RK_REONBOARDED = "employee.reonboarded";
    public static final String RK_OFFBOARDED  = "employee.offboarded";
    public static final String RK_UPDATED     = "employee.updated";

    @Bean
    public TopicExchange employeeEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue authHubEmployeeOnboardedQueue() {
        return new Queue(QUEUE_AUTHHUB_ONBOARDED, true);
    }

    @Bean
    public Queue authHubEmployeeOffboardedQueue() {
        return new Queue(QUEUE_AUTHHUB_OFFBOARDED, true);
    }

    @Bean
    public Binding bindOnboarded() {
        return BindingBuilder.bind(authHubEmployeeOnboardedQueue())
                .to(employeeEventsExchange()).with(RK_ONBOARDED);
    }

    @Bean
    public Binding bindReonboarded() {
        return BindingBuilder.bind(authHubEmployeeOnboardedQueue())
                .to(employeeEventsExchange()).with(RK_REONBOARDED);
    }

    @Bean
    public Binding bindOffboarded() {
        return BindingBuilder.bind(authHubEmployeeOffboardedQueue())
                .to(employeeEventsExchange()).with(RK_OFFBOARDED);
    }

    // ── USER_CREATED event (published by AuthHub → consumed by NovaDesk) ──

    public static final String QUEUE_USER_CREATED = "novadesk.employee.user-created";

    @Bean
    public TopicExchange userEventsExchange() {
        return new TopicExchange("user-events", true, false);
    }

    @Bean
    public Queue novadeskUserCreatedQueue() {
        return new Queue(QUEUE_USER_CREATED, true);
    }

    @Bean
    public Binding bindUserCreated() {
        return BindingBuilder.bind(novadeskUserCreatedQueue())
                .to(userEventsExchange()).with("user.created");
    }
}
