package com.af.novadesk.api.common.service.impl;

import com.af.novadesk.api.common.constants.EmployeeStatus;
import com.af.novadesk.api.common.dto.EmployeeDto;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.service.EmployeeQueryService;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeQueryServiceImpl implements EmployeeQueryService {

    private final CmEmployeeRepository  employeeRepository;
    private final FinanceSecurityContext securityContext;

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

    @Override
    public PageResponse<EmployeeDto> listFiltered(String q, EmployeeStatus status,
                                                   UUID legalEntityId, UUID managerId,
                                                   Pageable pageable) {
        UUID orgId = securityContext.getOrganizationId();
        return PageResponse.of(
                employeeRepository.findAll(
                        CmEmployeeRepository.filterSpec(orgId, q, status, legalEntityId, managerId),
                        pageable
                ).map(this::toDto)
        );
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
