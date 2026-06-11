package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.BankReconciliationApi;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.DuplicateStatementWarningDto;
import com.af.novadesk.api.finance.service.BankStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Controller for Bank Statement upload and management (LLR-BNK-01.1).
 *
 * <p>Delegates all business logic to {@link BankStatementService}.
 * Uses {@code @RequestParam} for the JSON form field to avoid
 * Content-Type issues with multipart requests from various clients.</p>
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class BankReconciliationController implements BankReconciliationApi {

    private final BankStatementService statementService;

    // =========================================================================
    // POST /upload
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> uploadStatement(
            UUID entityId,
            UUID bankAccountId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String filePassword,
            String notes,
            MultipartFile file) {

        log.debug("Received bank statement upload. Entity: {}, Account: {}, Period: {} to {}, File: {}",
                  entityId, bankAccountId, periodStart, periodEnd, file.getOriginalFilename());

        BankStatementUploadRequest request = BankStatementUploadRequest.builder()
                .entityId(entityId)
                .bankAccountId(bankAccountId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .filePassword(filePassword)
                .notes(notes)
                .build();

        BankStatementDto result = statementService.uploadStatement(request, file);

        log.info("Bank statement uploaded successfully: {}", result.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Statement uploaded and parsed successfully", result));
    }

    // =========================================================================
    // POST /{id}/parse
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> parseStatement(UUID statementId) {
        BankStatementDto result = statementService.parseStatement(statementId);
        return ResponseEntity.ok(ApiResponse.success(200, "Statement parsed successfully", result));
    }

    // =========================================================================
    // POST /{id}/replace
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> replaceStatement(
            UUID existingStatementId,
            UUID entityId,
            UUID bankAccountId,
            LocalDate periodStart,
            LocalDate periodEnd,
            String filePassword,
            String notes,
            MultipartFile file) {

        log.debug("Replacing statement {}. Entity: {}, Account: {}, Period: {} to {}, File: {}",
                  existingStatementId, entityId, bankAccountId, periodStart, periodEnd, file.getOriginalFilename());

        BankStatementUploadRequest request = BankStatementUploadRequest.builder()
                .entityId(entityId)
                .bankAccountId(bankAccountId)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .filePassword(filePassword)
                .notes(notes)
                .build();

        BankStatementDto result = statementService.replaceStatement(existingStatementId, request, file);

        log.info("Bank statement replaced successfully. Old ID: {}, New ID: {}", existingStatementId, result.getId());

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Statement replaced successfully", result));
    }

    // =========================================================================
    // GET /check-duplicate
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<DuplicateStatementWarningDto>> checkDuplicate(
            UUID bankAccountId,
            LocalDate periodStart,
            LocalDate periodEnd) {

        DuplicateStatementWarningDto result = statementService.checkDuplicate(
                bankAccountId, periodStart, periodEnd);
        if (result == null) {
            return ResponseEntity.ok(ApiResponse.success(200, "No duplicate found", null));
        }
        return ResponseEntity.ok(ApiResponse.success(200, "Duplicate statement found", result));
    }

    // =========================================================================
    // GET /{id}
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementDto>> getStatement(UUID statementId) {
        BankStatementDto result = statementService.getStatementById(statementId);
        return ResponseEntity.ok(ApiResponse.success(200, "Statement retrieved successfully", result));
    }

    // =========================================================================
    // GET /
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankStatementPageDto>> listStatements(
            UUID legalEntityId,
            int page,
            int size) {

        BankStatementPageDto result = statementService.listStatements(legalEntityId, page, size);
        return ResponseEntity.ok(ApiResponse.success(200, "Statements retrieved successfully", result));
    }
}
