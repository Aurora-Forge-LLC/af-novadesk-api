package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.entity.EmployeeOutboxEvent;

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
     * @param cmEmployee   the newly created CmEmployee
     * @param assignment   the entity assignment for the employee
     * @param entityRole   the entity role to assign ("MANAGER" or "VIEWER")
     * @param isManager    whether the employee has a manager role
     * @return the persisted outbox event
     */
    EmployeeOutboxEvent createOnboardedEvent(CmEmployee cmEmployee, CmEmployeeEntityAssignment assignment, String entityRole, boolean isManager);

    /**
     * Creates an EMPLOYEE_ROLE_CHANGED outbox event for existing employees
     * whose manager role is toggled via PATCH.
     *
     * @param cmEmployee   the updated CmEmployee
     * @param assignment   the entity assignment for the employee
     * @param entityRole   the new entity role ("MANAGER" or "VIEWER")
     * @param isManager    whether the employee now has a manager role
     * @return the persisted outbox event
     */
    EmployeeOutboxEvent createRoleChangedEvent(CmEmployee cmEmployee, CmEmployeeEntityAssignment assignment, String entityRole, boolean isManager);
}
