package com.af.novadesk.api.asset.service.impl;

import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.dto.AssetPayrollDeductionDto;
import com.af.novadesk.api.asset.entity.AssetPayrollDeduction;
import com.af.novadesk.api.asset.repository.AssetPayrollDeductionRepository;
import com.af.novadesk.api.asset.service.AssetPayrollDeductionService;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.finance.exception.BadRequestException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AssetPayrollDeductionServiceImpl implements AssetPayrollDeductionService {

    private final AssetPayrollDeductionRepository deductionRepository;
    private final CmEmployeeRepository            cmEmployeeRepository;

    @Override
    public List<PendingDeduction> findPendingForPeriod(UUID orgId, UUID employeeId,
                                                       LocalDate periodStart, LocalDate periodEnd) {
        return deductionRepository
                .findPendingByEmployeeAndPeriod(orgId, employeeId, periodStart, periodEnd)
                .stream()
                .map(d -> new PendingDeduction(
                        d.getId(),
                        d.getEmployeeId(),
                        d.getAmount(),
                        d.getCurrencyCode(),
                        d.getWriteOffReason(),
                        d.getAssetLabel()))
                .toList();
    }

    @Override
    @Transactional
    public void markApplied(UUID deductionId, UUID payrollBatchId, UUID payslipId) {
        AssetPayrollDeduction deduction = deductionRepository.findById(deductionId)
                .orElseThrow(() -> new BadRequestException("Asset payroll deduction not found: " + deductionId));
        deduction.setDeductionStatus(AssetDeductionStatus.APPLIED);
        deduction.setPayrollBatchId(payrollBatchId);
        deduction.setPayslipId(payslipId);
        deductionRepository.save(deduction);
        log.info("Asset deduction {} marked APPLIED — batch={}, payslip={}", deductionId, payrollBatchId, payslipId);
    }

    @Override
    public Page<AssetPayrollDeductionDto> listDeductions(UUID orgId, AssetDeductionStatus status,
                                                          UUID employeeId, LocalDate from, LocalDate to,
                                                          Pageable pageable) {
        Page<AssetPayrollDeduction> page = deductionRepository
                .findAllFiltered(orgId, status, employeeId, from, to, pageable);

        // Batch-resolve employee display names
        Set<UUID> empIds = page.stream()
                .map(AssetPayrollDeduction::getEmployeeId)
                .collect(Collectors.toSet());
        Map<UUID, String> namesByEmpId = empIds.isEmpty() ? Map.of()
                : cmEmployeeRepository.findAllById(empIds).stream()
                        .collect(Collectors.toMap(CmEmployee::getId, CmEmployee::getDisplayName));

        return page.map(d -> toDto(d, namesByEmpId.get(d.getEmployeeId())));
    }

    @Override
    public AssetPayrollDeductionDto getByWriteOffId(UUID writeOffId, UUID orgId) {
        AssetPayrollDeduction deduction = deductionRepository
                .findByWriteOffIdAndOrganizationId(writeOffId, orgId)
                .orElseThrow(() -> new BadRequestException(
                        "No payroll deduction found for write-off: " + writeOffId));
        String employeeName = cmEmployeeRepository.findById(deduction.getEmployeeId())
                .map(CmEmployee::getDisplayName)
                .orElse(null);
        return toDto(deduction, employeeName);
    }

    private AssetPayrollDeductionDto toDto(AssetPayrollDeduction d, String employeeName) {
        return AssetPayrollDeductionDto.builder()
                .id(d.getId())
                .writeOffId(d.getWriteOff().getId())
                .assetId(d.getWriteOff().getAsset().getId())
                .assetLabel(d.getAssetLabel())
                .employeeId(d.getEmployeeId())
                .employeeName(employeeName)
                .amount(d.getAmount())
                .currencyCode(d.getCurrencyCode())
                .deductionDate(d.getDeductionDate())
                .writeOffReason(d.getWriteOffReason())
                .deductionStatus(d.getDeductionStatus())
                .payrollBatchId(d.getPayrollBatchId())
                .payslipId(d.getPayslipId())
                .createdAt(d.getCreatedAt())
                .build();
    }
}
