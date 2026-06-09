package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.payroll.constants.PayrollAuditAction;
import com.af.novadesk.api.payroll.dto.PayrollAuditLogDto;
import com.af.novadesk.api.payroll.entity.Employee;
import com.af.novadesk.api.payroll.entity.PayrollAuditLog;
import com.af.novadesk.api.payroll.exception.EmployeeNotFoundException;
import com.af.novadesk.api.payroll.repository.EmployeeRepository;
import com.af.novadesk.api.payroll.repository.PayrollAuditLogRepository;
import com.af.novadesk.api.payroll.service.PayrollAuditLogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayrollAuditLogServiceImpl implements PayrollAuditLogService {

    private final PayrollAuditLogRepository repository;
    private final EmployeeRepository employeeRepository;

    public PayrollAuditLogServiceImpl(PayrollAuditLogRepository repository,
                                      EmployeeRepository employeeRepository) {
        this.repository = repository;
        this.employeeRepository = employeeRepository;
    }

    @Override
    public void log(PayrollAuditAction action, String entityType, UUID entityId,
                    UUID performedByEmployeeId, String details, String changeSnapshot) {
        Employee performedBy = null;
        if (performedByEmployeeId != null) {
            performedBy = employeeRepository.findById(performedByEmployeeId)
                    .orElseThrow(() -> new EmployeeNotFoundException(performedByEmployeeId));
        }

        PayrollAuditLog log = PayrollAuditLog.builder()
                .organizationId(performedBy != null ? performedBy.getOrganizationId() : null)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .performedBy(performedBy)
                .details(details)
                .changeSnapshot(changeSnapshot)
                .status(Status.ACTIVE)
                .build();
        repository.save(log);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayrollAuditLogDto> getAuditLogs(String entityType, UUID entityId) {
        return repository.findByEntityTypeAndEntityIdOrderByCreatedAtDesc(entityType, entityId).stream()
                .map(this::toDto).collect(Collectors.toList());
    }

    private PayrollAuditLogDto toDto(PayrollAuditLog log) {
        return PayrollAuditLogDto.builder()
                .id(log.getId())
                .organizationId(log.getOrganizationId())
                .action(log.getAction())
                .entityType(log.getEntityType())
                .entityId(log.getEntityId())
                .performedById(log.getPerformedBy() != null ? log.getPerformedBy().getId() : null)
                .performedByName(log.getPerformedBy() != null
                        ? null : null) // TODO: resolve from CmEmployee via cmEmployeeId in Phase 5 cleanup
                .details(log.getDetails())
                .changeSnapshot(log.getChangeSnapshot())
                .createdAt(log.getCreatedAt())
                .build();
    }
}
