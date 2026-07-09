package com.af.novadesk.api.common.service;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.response.PageResponse;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Cross-module read interface for employee identity data.
 * All modules (asset, payroll, leave) use this to look up employees
 * without importing module-specific repositories.
 */
public interface EmployeeQueryService {

    /** All ACTIVE employees assigned to a legal entity — used for asset assignment dropdown. */
    List<EmployeeDto> listByEntity(UUID legalEntityId);

    /** Paginated, filterable employee list for list-view screens. */
    PageResponse<EmployeeDto> listFiltered(String q, EmployeeStatus status,
                                           UUID legalEntityId, UUID managerId,
                                           Pageable pageable);

    /** Lookup by employee id — org-scoped to prevent cross-org leaks. */
    EmployeeDto getById(UUID employeeId, UUID organizationId);

    /** Check if an employee exists in the org — used for validation. */
    boolean existsInOrg(UUID employeeId, UUID organizationId);
}
