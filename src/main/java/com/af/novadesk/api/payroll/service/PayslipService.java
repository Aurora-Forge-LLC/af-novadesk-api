package com.af.novadesk.api.payroll.service;

import com.af.novadesk.api.payroll.dto.PayslipDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for Payslip management (LLR-PAY-02.5, PAY-04).
 */
public interface PayslipService {

    PayslipDto getPayslip(UUID payslipId);

    PayslipDto getPayslipByEmployeeAndBatch(UUID employeeId, UUID batchId);

    List<PayslipDto> listPayslipsByEmployee(UUID employeeId);

    List<PayslipDto> listPayslipsByBatch(UUID batchId);

    PayslipDto generatePayslipPdf(UUID payslipId);

    byte[] downloadPayslipPdf(UUID payslipId);
}
