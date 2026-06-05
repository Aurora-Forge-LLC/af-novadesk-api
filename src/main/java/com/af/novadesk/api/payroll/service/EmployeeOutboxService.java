package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.EmployeeOutboxEvent;

/**
 * Service contract for persisting employee lifecycle outbox events.
 *
 * <p>Called from {@code EmployeeServiceImpl} inside the same
 * {@code @Transactional} as the CRUD operation.</p>
 */
public interface EmployeeOutboxService {

    /**
     * Creates an EMPLOYEE_ONBOARDED outbox event.
     *
     * @param employee     the newly created Employee
     * @param entityRole   the entity role to assign ("MANAGER" or "VIEWER")
     * @param isManager    whether the employee has a manager role
     * @return the persisted outbox event
     */
    EmployeeOutboxEvent createOnboardedEvent(Employee employee, String entityRole, boolean isManager);

    /**
     * Creates an EMPLOYEE_ROLE_CHANGED outbox event for existing employees
     * whose manager role is toggled via PATCH.
     *
     * @param employee     the updated Employee
     * @param entityRole   the new entity role ("MANAGER" or "VIEWER")
     * @param isManager    whether the employee now has a manager role
     * @return the persisted outbox event
     */
    EmployeeOutboxEvent createRoleChangedEvent(Employee employee, String entityRole, boolean isManager);
}
