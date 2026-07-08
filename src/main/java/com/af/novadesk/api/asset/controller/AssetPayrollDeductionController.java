package com.af.novadesk.api.asset.controller;

import com.af.novadesk.api.asset.api.AssetPayrollDeductionApi;
import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.dto.AssetPayrollDeductionDto;
import com.af.novadesk.api.asset.service.AssetPayrollDeductionService;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssetPayrollDeductionController implements AssetPayrollDeductionApi {

    private final AssetPayrollDeductionService deductionService;
    private final FinanceSecurityContext       securityContext;

    @Override
    public ResponseEntity<ApiResponse<Page<AssetPayrollDeductionDto>>> listDeductions(
            AssetDeductionStatus status, UUID employeeId,
            LocalDate from, LocalDate to, Pageable pageable) {
        UUID orgId = securityContext.getOrganizationId();
        Page<AssetPayrollDeductionDto> page =
                deductionService.listDeductions(orgId, status, employeeId, from, to, pageable);
        return ResponseBuilder.ok(page, "Asset payroll deductions retrieved");
    }

    @Override
    public ResponseEntity<ApiResponse<AssetPayrollDeductionDto>> getByWriteOffId(UUID writeOffId) {
        UUID orgId = securityContext.getOrganizationId();
        return ResponseBuilder.ok(
                deductionService.getByWriteOffId(writeOffId, orgId),
                "Asset payroll deduction retrieved");
    }
}
