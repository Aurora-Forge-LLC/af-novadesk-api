package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.BankReconciliationApi;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.BankTransactionPageDto;
import com.af.novadesk.api.finance.exception.BadRequestException;
import com.af.novadesk.api.finance.service.BankReconciliationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST controller for Bank Reconciliation (LLR-BNK-01).
 *
 * <p>Implements {@link BankReconciliationApi} and delegates all operations
 * to {@link BankReconciliationService}.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BankReconciliationController implements BankReconciliationApi {

    private final BankReconciliationService bankReconciliationService;
    private final ObjectMapper objectMapper;

    // =========================================================================
    // LLR-BNK-01.1: Upload
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> uploadStatement(
            String requestJson, MultipartFile file) {

        BankStatementUploadRequest request = deserializeRequest(requestJson);
        BankStatementDto result = bankReconciliationService.uploadStatement(request, file);
        log.info("Bank statement uploaded: id={}, file={}, entity={}",
                result.getId(), result.getOriginalFilename(), result.getLegalEntityId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Bank statement uploaded successfully", result));
    }

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> replaceStatement(
            String requestJson, MultipartFile file) {

        BankStatementUploadRequest request = deserializeRequest(requestJson);
        BankStatementDto result = bankReconciliationService.replaceStatement(request, file);
        log.info("Bank statement replaced: id={}, file={}, entity={}",
                result.getId(), result.getOriginalFilename(), result.getLegalEntityId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Bank statement replaced successfully", result));
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private BankStatementUploadRequest deserializeRequest(String requestJson) {
        try {
            return objectMapper.readValue(requestJson, BankStatementUploadRequest.class);
        } catch (Exception e) {
            log.warn("Failed to deserialize upload request JSON: {}", requestJson, e);
            throw new BadRequestException(
                    "Invalid request format. The 'request' field must be a valid JSON string " +
                    "(use JSON.stringify() on the frontend), containing entityId, bankAccountId, " +
                    "periodStart, periodEnd (and optionally filePassword, notes). " +
                    "Example: JSON.stringify({entityId:'uuid', bankAccountId:'uuid', " +
                    "periodStart:'2026-01-01', periodEnd:'2026-01-31'})");
        }
    }

    // =========================================================================
    // LLR-BNK-01: Statement list & detail
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementPageDto>> listStatements(
            UUID legalEntityId, int page, int size) {

        BankStatementPageDto result = bankReconciliationService.listStatements(legalEntityId, page, size);
        return ResponseEntity.ok()
                .body(ApiResponse.success(200, "Statements retrieved successfully", result));
    }

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> getStatement(UUID id) {
        BankStatementDto result = bankReconciliationService.getStatement(id);
        return ResponseEntity.ok()
                .body(ApiResponse.success(200, "Statement retrieved successfully", result));
    }

    // =========================================================================
    // LLR-BNK-01: Transaction list
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankTransactionPageDto>> listTransactions(
            UUID statementId, int page, int size) {

        BankTransactionPageDto result = bankReconciliationService.listTransactions(statementId, page, size);
        return ResponseEntity.ok()
                .body(ApiResponse.success(200, "Transactions retrieved successfully", result));
    }
}
