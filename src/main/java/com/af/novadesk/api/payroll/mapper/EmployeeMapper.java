package com.af.novadesk.api.payroll.mapper;

import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.PayrollDetails;
import com.af.novadesk.api.payroll.repository.PayrollDetailsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Maps between {@link Employee} entity and {@link EmployeeDto}.
 * Composes identity data from {@link CmEmployee} and compensation from {@link PayrollDetails}.
 */
@Component
@RequiredArgsConstructor
public class EmployeeMapper {

    private final CmEmployeeRepository   cmEmployeeRepository;
    private final PayrollDetailsRepository payrollDetailsRepository;

    public EmployeeDto toDto(Employee entity) {
        if (entity == null) return null;

        // Resolve identity from cm_employees
        CmEmployee cm = entity.getCmEmployeeId() != null
                ? cmEmployeeRepository.findById(entity.getCmEmployeeId()).orElse(null)
                : null;

        // Resolve compensation from pr_payroll_details
        PayrollDetails pd = entity.getCmEmployeeId() != null
                ? payrollDetailsRepository.findByEmployeeId(entity.getCmEmployeeId()).orElse(null)
                : null;

        EmployeeDto dto = EmployeeDto.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganizationId())
                .authUserId(cm != null ? cm.getAuthUserId() : null)
                .employeeCode(cm != null ? cm.getEmployeeCode() : null)
                .firstName(cm != null ? extractFirstName(cm.getDisplayName()) : null)
                .lastName(cm != null ? extractLastName(cm.getDisplayName()) : null)
                .email(cm != null ? cm.getEmail() : null)
                .displayName(cm != null ? cm.getDisplayName() : null)
                .baseSalary(pd != null ? pd.getBaseSalary() : null)
                .salaryCurrency(pd != null ? pd.getSalaryCurrency() : null)
                .bankAccountNumber(pd != null ? pd.getBankAccountNumber() : null)
                .bankName(pd != null ? pd.getBankName() : null)
                .bankIfscCode(pd != null ? pd.getBankIfscCode() : null)
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .build();

        if (entity.getManager() != null) {
            dto.setManagerId(entity.getManager().getId());
            // Manager display name resolved lazily from their own cm_employee
            CmEmployee managerCm = entity.getManager().getCmEmployeeId() != null
                    ? cmEmployeeRepository.findById(entity.getManager().getCmEmployeeId()).orElse(null)
                    : null;
            if (managerCm != null) dto.setManagerName(managerCm.getDisplayName());
        }

        return dto;
    }

    /** Merges updatable fields into an existing entity (partial update). */
    public void updateEntity(Employee entity, EmployeeDto dto) {
        // Identity and compensation fields are now managed via common module
        // and pr_payroll_details. This method is a no-op for thin Employee fields.
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
