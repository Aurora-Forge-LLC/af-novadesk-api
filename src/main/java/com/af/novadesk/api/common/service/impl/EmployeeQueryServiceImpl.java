package com.af.novadesk.api.common.service.impl;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.service.EmployeeQueryService;
import com.af.novadesk.api.finance.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeQueryServiceImpl implements EmployeeQueryService {

    private final CmEmployeeRepository employeeRepository;

    @Override
    public List<EmployeeDto> listByEntity(UUID legalEntityId) {
        return employeeRepository
                .findAllByLegalEntityIdAndStatus(legalEntityId, EmployeeStatus.ACTIVE)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Override
    public EmployeeDto getById(UUID employeeId, UUID organizationId) {
        return employeeRepository.findByIdAndOrganizationId(employeeId, organizationId)
                .map(this::toDto)
                .orElseThrow(() -> new BadRequestException(
                        "Employee not found: " + employeeId));
    }

    @Override
    public boolean existsInOrg(UUID employeeId, UUID organizationId) {
        return employeeRepository.findByIdAndOrganizationId(employeeId, organizationId).isPresent();
    }

    private EmployeeDto toDto(CmEmployee e) {
        EmployeeDto dto = new EmployeeDto();
        dto.setId(e.getId());
        dto.setOrganizationId(e.getOrganizationId());
        dto.setEmployeeCode(e.getEmployeeCode());
        dto.setDisplayName(e.getDisplayName());
        dto.setEmail(e.getEmail());
        dto.setEmployeeStatus(e.getEmployeeStatus().name());
        return dto;
    }
}
