package com.af.novadesk.api.common.event;

import lombok.Getter;

import java.util.UUID;

/**
 * Published by the Payroll module after an employee is successfully onboarded.
 * Consumed by the Finance module to grant EntityUserAccess.
 *
 * <p>This is a lightweight POJO in the shared common package. It carries
 * the minimum data needed for cross-module event consumers without coupling
 * them to each other's entity classes.</p>
 *
 * <p>The {@code idempotencyKey} follows the convention:
 * {@code EMPLOYEE_ONBOARDED:<employeeId>}.</p>
 */
@Getter
public class EmployeeOnboardedEvent {

    private final UUID employeeId;
    private final UUID authUserId;
    private final UUID organizationId;
    private final UUID legalEntityId;
    private final String employeeCode;
    private final String displayName;
    private final String email;
    private final boolean isManager;
    private final String entityRole;          // "MANAGER" or "VIEWER"
    private final String idempotencyKey;      // "EMPLOYEE_ONBOARDED:" + employeeId

    public EmployeeOnboardedEvent(
            UUID employeeId,
            UUID authUserId,
            UUID organizationId,
            UUID legalEntityId,
            String employeeCode,
            String displayName,
            String email,
            boolean isManager,
            String entityRole) {
        this.employeeId = employeeId;
        this.authUserId = authUserId;
        this.organizationId = organizationId;
        this.legalEntityId = legalEntityId;
        this.employeeCode = employeeCode;
        this.displayName = displayName;
        this.email = email;
        this.isManager = isManager;
        this.entityRole = entityRole;
        this.idempotencyKey = "EMPLOYEE_ONBOARDED:" + employeeId;
    }
}
