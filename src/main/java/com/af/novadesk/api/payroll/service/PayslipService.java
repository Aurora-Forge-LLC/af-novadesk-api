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

    /**
     * List payslips filtered by optional employeeId and/or legalEntityId.
     * <ul>
     *   <li>If only {@code employeeId} is provided — returns payslips for that employee.</li>
     *   <li>If only {@code legalEntityId} is provided — returns payslips for all employees in that entity.</li>
     *   <li>If both are provided — returns payslips for that employee within that entity.</li>
     *   <li>If neither is provided — returns an empty list (caller must specify at least one filter).</li>
     * </ul>
     *
     * @param employeeId    optional employee UUID
     * @param legalEntityId optional legal entity UUID
     * @return filtered list of payslips
     */
    List<PayslipDto> listPayslips(UUID employeeId, UUID legalEntityId);

    List<PayslipDto> listPayslipsByBatch(UUID batchId);

    PayslipDto generatePayslipPdf(UUID payslipId);

    byte[] downloadPayslipPdf(UUID payslipId);
}
