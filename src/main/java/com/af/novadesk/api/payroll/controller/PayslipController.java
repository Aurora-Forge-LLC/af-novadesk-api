package com.af.novadesk.api.payroll.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.entity.CmEmployee;
import com.af.novadesk.api.common.repository.CmEmployeeRepository;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.response.PageResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.payroll.api.PayslipApi;
import com.af.novadesk.api.payroll.dto.PayslipDto;
import com.af.novadesk.api.payroll.service.PayslipService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
public class PayslipController implements PayslipApi {

    private final PayslipService payslipService;
    private final CmEmployeeRepository cmEmployeeRepository;

    public PayslipController(PayslipService payslipService,
                             CmEmployeeRepository cmEmployeeRepository) {
        this.payslipService = payslipService;
        this.cmEmployeeRepository = cmEmployeeRepository;
    }

    // -------------------------------------------------------------------------
    // JWT helper methods
    // -------------------------------------------------------------------------

    private List<String> getRolesFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            List<String> roles = jwt.getClaim("roles");
            return roles != null ? roles : List.of();
        }
        return List.of();
    }

    private UUID getAuthUserIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            return UUID.fromString(jwt.getSubject());
        }
        throw new IllegalStateException("No authenticated JWT principal found");
    }

    private UUID getOrganizationIdFromJwt() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Jwt jwt) {
            String orgId = jwt.getClaimAsString("organizationId");
            if (orgId != null) {
                return UUID.fromString(orgId);
            }
        }
        throw new IllegalStateException("No organizationId claim found in JWT");
    }

    /**
     * Returns true if the authenticated user has the EMPLOYEE role in their JWT.
     */
    private boolean isEmployeeRole() {
        return getRolesFromJwt().stream()
                .anyMatch(r -> "EMPLOYEE".equalsIgnoreCase(r));
    }

    /**
     * Resolves the authenticated user's (authUserId) corresponding employee record ID.
     */
    private UUID getMyEmployeeId() {
        UUID authUserId = getAuthUserIdFromJwt();
        UUID orgId = getOrganizationIdFromJwt();
        return cmEmployeeRepository.findByAuthUserIdAndOrganizationId(authUserId, orgId)
                .map(CmEmployee::getId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No employee record found for authenticated user " + authUserId));
    }

    /**
     * Returns the effective employeeId to use for a query.
     * If the caller has the EMPLOYEE role, forces the value to their own employee ID.
     * Otherwise, returns the requested employeeId as-is.
     */
    private UUID resolveEmployeeId(UUID requestedEmployeeId) {
        if (isEmployeeRole()) {
            return getMyEmployeeId();
        }
        return requestedEmployeeId;
    }

    // -------------------------------------------------------------------------
    // Endpoint implementations
    // -------------------------------------------------------------------------

    @Override
    public ResponseEntity<ApiResponse<PageResponse<PayslipDto>>> listPayslips(
            UUID employeeId,
            UUID batchId,
            LocalDate fromDate,
            LocalDate toDate,
            Boolean isDownloaded,
            int page,
            int size) {
        UUID orgId = getOrganizationIdFromJwt();
        // EMPLOYEE role is scoped to their own employee record
        UUID effectiveEmployeeId = isEmployeeRole() ? getMyEmployeeId() : employeeId;
        PageResponse<PayslipDto> result = payslipService.listPayslipsFiltered(
                orgId, effectiveEmployeeId, batchId, fromDate, toDate, isDownloaded, page, size);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<PayslipDto>> getPayslip(UUID id) {
        PayslipDto result = payslipService.getPayslip(id);
        // EMPLOYEE role can only view their own payslips
        if (isEmployeeRole()) {
            UUID myEmployeeId = getMyEmployeeId();
            if (!myEmployeeId.equals(result.getEmployeeId())) {
                throw new IllegalArgumentException("Access denied: payslip does not belong to you");
            }
        }
        return ResponseBuilder.ok(result, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<byte[]> downloadPdf(UUID id) {
        // EMPLOYEE role can only download their own payslips
        if (isEmployeeRole()) {
            PayslipDto payslip = payslipService.getPayslip(id);
            UUID myEmployeeId = getMyEmployeeId();
            if (!myEmployeeId.equals(payslip.getEmployeeId())) {
                throw new IllegalArgumentException("Access denied: payslip does not belong to you");
            }
        }
        byte[] pdf = payslipService.downloadPayslipPdf(id);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=payslip-" + id + ".pdf")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @Override
    public ResponseEntity<ApiResponse<List<PayslipDto>>> listByBatch(UUID batchId) {
        List<PayslipDto> result = payslipService.listPayslipsByBatch(batchId);
        return ResponseBuilder.ok(result, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }
}
