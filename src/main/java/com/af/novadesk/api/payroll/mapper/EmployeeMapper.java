package com.af.novadesk.api.payroll.mapper;

import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.entity.CmEmployeeEntityAssignment;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps between {@link CmEmployee} entity and {@link EmployeeDto}.
 * Composes identity data from {@link CmEmployee}, compensation from {@link PayrollDetails},
 * and assignment data from {@link CmEmployeeEntityAssignment}.
 */
@Component
@RequiredArgsConstructor
public class EmployeeMapper {

    private final CmEmployeeRepository   cmEmployeeRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;

    public EmployeeDto toDto(CmEmployee cm, CmEmployeeEntityAssignment assignment) {
        if (cm == null) return null;

        // Resolve compensation from pr_payroll_details
        PayrollDetails pd = cm.getId() != null
                ? payrollDetailsRepository.findByEmployeeId(cm.getId()).orElse(null)
                : null;

        EmployeeDto dto = EmployeeDto.builder()
                .id(cm.getId())
                .organizationId(cm.getOrganizationId())
                .authUserId(cm.getAuthUserId())
                .employeeCode(cm.getEmployeeCode())
                .firstName(extractFirstName(cm.getDisplayName()))
                .lastName(extractLastName(cm.getDisplayName()))
                .email(cm.getEmail())
                .displayName(cm.getDisplayName())
                .baseSalary(pd != null ? pd.getBaseSalary() : null)
                .salaryCurrency(pd != null ? pd.getSalaryCurrency() : null)
                .bankAccountNumber(pd != null ? pd.getBankAccountNumber() : null)
                .bankName(pd != null ? pd.getBankName() : null)
                .bankIfscCode(pd != null ? pd.getBankIfscCode() : null)
                .createdAt(cm.getCreatedAt())
                .updatedAt(cm.getUpdatedAt())
                .status(cm.getEmployeeStatus() != null ? cm.getEmployeeStatus().name() : null)
                .build();

        if (assignment != null) {
            dto.setLegalEntityId(assignment.getLegalEntity() != null
                    ? assignment.getLegalEntity().getId() : null);
            dto.setDepartment(assignment.getDepartment());
            dto.setDepartmentId(assignment.getDepartmentId());
            dto.setDesignation(assignment.getDesignation());
            dto.setHireDate(assignment.getHireDate());
            dto.setTerminationDate(assignment.getTerminationDate());
        }

        // Manager
        if (cm.getManager() != null) {
            dto.setManagerId(cm.getManager().getId());
            CmEmployee managerCm = cmEmployeeRepository.findById(cm.getManager().getId()).orElse(null);
            if (managerCm != null) dto.setManagerName(managerCm.getDisplayName());
        }

        return dto;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String extractFirstName(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        int idx = displayName.indexOf(' ');
        return idx > 0 ? displayName.substring(0, idx) : displayName;
    }

    private static String extractLastName(String displayName) {
        if (displayName == null || displayName.isBlank()) return null;
        int idx = displayName.indexOf(' ');
        return idx > 0 ? displayName.substring(idx + 1) : "";
    }
}
