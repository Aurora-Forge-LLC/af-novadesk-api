package com.af.novadesk.api.payroll.service.impl;

import com.af.novadesk.api.payroll.dto.PayslipDto;
import com.af.novadesk.api.payroll.entity.Payslip;
import com.af.novadesk.api.payroll.exception.PayslipNotFoundException;
import com.af.novadesk.api.payroll.mapper.PayrollBatchMapper;
import com.af.novadesk.api.payroll.repository.PayslipRepository;
import com.af.novadesk.api.payroll.service.PayslipService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class PayslipServiceImpl implements PayslipService {

    private final PayslipRepository payslipRepository;
    private final PayrollBatchMapper mapper;

    public PayslipServiceImpl(PayslipRepository payslipRepository,
                              PayrollBatchMapper mapper) {
        this.payslipRepository = payslipRepository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipDto getPayslip(UUID payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new PayslipNotFoundException(payslipId));
        return mapper.toPayslipDto(payslip);
    }

    @Override
    @Transactional(readOnly = true)
    public PayslipDto getPayslipByEmployeeAndBatch(UUID employeeId, UUID batchId) {
        return payslipRepository.findByPayrollBatchIdAndEmployeeId(batchId, employeeId)
                .map(mapper::toPayslipDto)
                .orElseThrow(() -> new PayslipNotFoundException(employeeId, batchId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayslipDto> listPayslipsByEmployee(UUID employeeId) {
        return payslipRepository.findByEmployeeIdOrderByPayPeriodStartDesc(employeeId).stream()
                .map(mapper::toPayslipDto).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayslipDto> listPayslips(UUID employeeId, UUID legalEntityId) {
        if (legalEntityId != null && employeeId != null) {
            return payslipRepository.findByLegalEntityIdAndEmployeeId(legalEntityId, employeeId).stream()
                    .map(mapper::toPayslipDto).collect(Collectors.toList());
        }
        if (legalEntityId != null) {
            return payslipRepository.findByLegalEntityId(legalEntityId).stream()
                    .map(mapper::toPayslipDto).collect(Collectors.toList());
        }
        if (employeeId != null) {
            return payslipRepository.findByEmployeeIdOrderByPayPeriodStartDesc(employeeId).stream()
                    .map(mapper::toPayslipDto).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayslipDto> listPayslipsByBatch(UUID batchId) {
        return payslipRepository.findByPayrollBatchId(batchId).stream()
                .map(mapper::toPayslipDto).collect(Collectors.toList());
    }

    @Override
    public PayslipDto generatePayslipPdf(UUID payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new PayslipNotFoundException(payslipId));
        // PDF generation placeholder — would use FileStorageService (MinIO)
        payslip.setPayslipPdfPath("/payslips/" + payslipId + ".pdf");
        payslip = payslipRepository.save(payslip);
        return mapper.toPayslipDto(payslip);
    }

    @Override
    @Transactional(readOnly = true)
    public byte[] downloadPayslipPdf(UUID payslipId) {
        Payslip payslip = payslipRepository.findById(payslipId)
                .orElseThrow(() -> new PayslipNotFoundException(payslipId));
        // Placeholder — would retrieve from MinIO via FileStorageService
        return new byte[0];
    }
}
