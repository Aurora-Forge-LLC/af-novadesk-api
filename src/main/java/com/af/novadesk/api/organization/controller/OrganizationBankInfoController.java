package com.af.novadesk.api.organization.controller;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.common.util.ResponseBuilder;
import com.af.novadesk.api.organization.api.OrganizationBankInfoApi;
import com.af.novadesk.api.organization.dto.BankAccountBalanceResponse;
import com.af.novadesk.api.organization.dto.OrganizationBankCreditRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoResponse;
import com.af.novadesk.api.organization.security.OrganizationSecurityContext;
import com.af.novadesk.api.organization.service.OrganizationBankInfoService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST controller implementing {@link OrganizationBankInfoApi}.
 *
 * <p>Extracts the authenticated user's {@code orgId} and {@code userId} from
 * the JWT via {@link OrganizationSecurityContext} and delegates to the
 * service layer.</p>
 */
@RestController
public class OrganizationBankInfoController implements OrganizationBankInfoApi {

    private final OrganizationBankInfoService bankInfoService;
    private final OrganizationSecurityContext securityContext;

    public OrganizationBankInfoController(OrganizationBankInfoService bankInfoService,
                                          OrganizationSecurityContext securityContext) {
        this.bankInfoService = bankInfoService;
        this.securityContext = securityContext;
    }

    @Override
    public ResponseEntity<ApiResponse<OrganizationBankInfoResponse>> create(OrganizationBankInfoRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID userId = securityContext.requireSuperAdminUserId();
        OrganizationBankInfoResponse data = bankInfoService.create(orgId, userId, request);
        return ResponseBuilder.created(data, ApiMessages.RECORD_CREATED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<OrganizationBankInfoResponse>> getById(UUID id) {
        UUID orgId = securityContext.getOrganizationId();
        OrganizationBankInfoResponse data = bankInfoService.getById(orgId, id);
        return ResponseBuilder.ok(data, ApiMessages.RECORD_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<List<OrganizationBankInfoResponse>>> list() {
        UUID orgId = securityContext.getOrganizationId();
        List<OrganizationBankInfoResponse> data = bankInfoService.list(orgId);
        return ResponseBuilder.ok(data, ApiMessages.RECORDS_RETRIEVED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<OrganizationBankInfoResponse>> update(UUID id, OrganizationBankInfoRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID userId = securityContext.requireSuperAdminUserId();
        OrganizationBankInfoResponse data = bankInfoService.update(orgId, userId, id, request);
        return ResponseBuilder.ok(data, ApiMessages.RECORD_UPDATED_SUCCESS);
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> delete(UUID id) {
        UUID orgId = securityContext.getOrganizationId();
        UUID userId = securityContext.requireSuperAdminUserId();
        bankInfoService.delete(orgId, userId, id);
        return ResponseBuilder.ok(null, ApiMessages.RECORD_DELETED_SUCCESS);
    }

    // ── Balance Operations ─────────────────────────────────────────────────

    @Override
    public ResponseEntity<ApiResponse<BankAccountBalanceResponse>> credit(UUID id,
                                                                           OrganizationBankCreditRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID userId = securityContext.requireSuperAdminUserId();
        BankAccountBalanceResponse data = bankInfoService.creditBalance(orgId, userId, id, request);
        return ResponseBuilder.ok(data, "Account credited successfully");
    }

    @Override
    public ResponseEntity<ApiResponse<BankAccountBalanceResponse>> debit(UUID id,
                                                                          OrganizationBankCreditRequest request) {
        UUID orgId = securityContext.getOrganizationId();
        UUID userId = securityContext.requireSuperAdminUserId();
        BankAccountBalanceResponse data = bankInfoService.debitBalance(orgId, userId, id, request);
        return ResponseBuilder.ok(data, "Account debited successfully");
    }
}
