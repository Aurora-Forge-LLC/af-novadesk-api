package com.af.novadesk.api.payroll.event;

/**
 * Employee lifecycle event types published to RabbitMQ
 * for AuthHub user provisioning and other downstream consumers.
 */
public enum EmployeeEventType {
    EMPLOYEE_ONBOARDED,
    EMPLOYEE_REONBOARDED,
    EMPLOYEE_OFFBOARDED,
    EMPLOYEE_UPDATED
}
