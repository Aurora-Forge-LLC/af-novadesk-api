package com.af.novadesk.api.payroll.mapper;

import com.af.novadesk.api.payroll.dto.EmployeeDto;
import com.af.novadesk.api.payroll.entity.Employee;
import org.springframework.stereotype.Component;

/**
 * Maps between {@link Employee} entity and {@link EmployeeDto}.
 */
@Component
public class EmployeeMapper {

    /**
     * Converts entity to DTO.
     *
     * @param entity the Employee entity
     * @return populated EmployeeDto
     */
    public EmployeeDto toDto(Employee entity) {
        if (entity == null) return null;

        EmployeeDto dto = EmployeeDto.builder()
                .id(entity.getId())
                .organizationId(entity.getOrganizationId())
                .authUserId(entity.getAuthUserId())
                .employeeCode(entity.getEmployeeCode())
                .firstName(entity.getFirstName())
                .lastName(entity.getLastName())
                .email(entity.getEmail())
                .displayName(entity.getFirstName() + " " + entity.getLastName())
                .department(entity.getDepartment())
                .designation(entity.getDesignation())
                .hireDate(entity.getHireDate())
                .terminationDate(entity.getTerminationDate())
                .baseSalary(entity.getBaseSalary())
                .salaryCurrency(entity.getSalaryCurrency())
                .bankAccountNumber(entity.getBankAccountNumber())
                .bankName(entity.getBankName())
                .bankIfscCode(entity.getBankIfscCode())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .status(entity.getStatus() != null ? entity.getStatus().name() : null)
                .build();

        // Resolve lazy-loaded relationships safely
        if (entity.getShadowUser() != null) {
            dto.setShadowUserId(entity.getShadowUser().getId());
        }
        if (entity.getLegalEntity() != null) {
            dto.setLegalEntityId(entity.getLegalEntity().getId());
            dto.setLegalEntityName(entity.getLegalEntity().getEntityName());
        }
        if (entity.getManager() != null) {
            dto.setManagerId(entity.getManager().getId());
            dto.setManagerName(entity.getManager().getFirstName() + " " + entity.getManager().getLastName());
        }

        return dto;
    }

    /**
     * Merges DTO fields into an existing entity (partial update support).
     */
    public void updateEntity(Employee entity, EmployeeDto dto) {
        if (dto.getDepartment() != null) entity.setDepartment(dto.getDepartment());
        if (dto.getDesignation() != null) entity.setDesignation(dto.getDesignation());
        if (dto.getBaseSalary() != null) entity.setBaseSalary(dto.getBaseSalary());
        if (dto.getSalaryCurrency() != null) entity.setSalaryCurrency(dto.getSalaryCurrency());
        if (dto.getBankAccountNumber() != null) entity.setBankAccountNumber(dto.getBankAccountNumber());
        if (dto.getBankName() != null) entity.setBankName(dto.getBankName());
        if (dto.getBankIfscCode() != null) entity.setBankIfscCode(dto.getBankIfscCode());
    }
}
