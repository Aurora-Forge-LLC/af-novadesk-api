package com.af.novadesk.api.payroll.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

/**
 * Event payload published to RabbitMQ for employee lifecycle events.
 *
 * <p>Consumed by af-authhub to provision/deprovision users
 * and by other downstream services for their own needs.</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmployeeEventPayload implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Pre-generated AuthHub user UUID */
    private UUID userId;

    /** NovaDesk internal employee ID */
    private UUID employeeId;

    /** Employee email address */
    private String email;

    /** Employee first name */
    private String firstName;

    /** Employee last name */
    private String lastName;

    /** Organization UUID (scope for AuthHub) */
    private UUID organizationId;
}
