package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.dto.LeavePolicyDto;
import com.af.novadesk.api.payroll.dto.LeavePolicyRequest;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for leave policy CRUD and balance sheet generation.
 */
public interface LeavePolicyService {

    // --- CRUD ---

    LeavePolicyDto createPolicy(LeavePolicyRequest request);

    LeavePolicyDto updatePolicy(UUID policyId, LeavePolicyRequest request);

    void deletePolicy(UUID policyId);

    LeavePolicyDto getPolicy(UUID policyId);

    List<LeavePolicyDto> listPoliciesByEntity(UUID legalEntityId);

    List<LeavePolicyDto> listPoliciesByOrganization(UUID organizationId);

    // --- Balance Sheet Generation ---

    /**
     * Generates LeaveBalance records for all active employees in the policy's
     * entity for the current fiscal year.
     */
    void generateBalanceSheetsForPolicy(UUID policyId);

    /**
     * Generates LeaveBalance records for a specific employee based on all
     * active policies in their entity.
     */
    void generateBalanceSheetsForEmployee(UUID employeeId, UUID legalEntityId);
}
