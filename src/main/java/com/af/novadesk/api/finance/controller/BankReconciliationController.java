package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.BankReconciliationApi;
import com.af.novadesk.api.finance.constants.MatchingMethod;
import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import com.af.novadesk.api.finance.dto.*;
import com.af.novadesk.api.finance.service.BankCategorizationService;
import com.af.novadesk.api.finance.service.BankMatchingService;
import com.af.novadesk.api.finance.service.BankStatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
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
    private final BankMatchingService bankMatchingService;
    private final BankCategorizationService categorizationService;

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

    // =========================================================================
    // GET /suggested-matches (LLR-BNK-02.4)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<SuggestedMatchPageDto>> getSuggestedMatches(int page, int size) {
        SuggestedMatchPageDto result = bankMatchingService.getSuggestedMatches(page, size);
        return ResponseEntity.ok(ApiResponse.success(200, "Suggested matches retrieved successfully", result));
    }

    // =========================================================================
    // POST /suggested-matches/{id}/resolve (LLR-BNK-02.4)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<SuggestedMatchDto>> resolveSuggestion(
            UUID suggestionId, ResolveSuggestionRequest request) {
        SuggestedMatchDto result = bankMatchingService.resolveSuggestion(suggestionId, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Suggestion resolved successfully", result));
    }

    // =========================================================================
    // GET /transactions (LLR-BNK-03)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankTransactionPageDto>> listTransactions(
            UUID entityId, UUID bankAccountId,
            LocalDate dateFrom, LocalDate dateTo,
            java.math.BigDecimal amountMin, java.math.BigDecimal amountMax,
            String search,
            ReconciliationStatus reconciliationStatus, MatchingMethod matchingMethod,
            int page, int size, String sortBy, String sortDir) {

        BankTransactionPageDto result = bankMatchingService.listTransactions(
                entityId, bankAccountId, dateFrom, dateTo,
                amountMin, amountMax, search, reconciliationStatus, matchingMethod,
                page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(200, "Transactions retrieved successfully", result));
    }

    // =========================================================================
    // GET /transactions/unmatched (LLR-BNK-03.1)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<BankTransactionPageDto>> getUnmatchedTransactions(
            UUID entityId, UUID bankAccountId,
            LocalDate dateFrom, LocalDate dateTo,
            java.math.BigDecimal amountMin, java.math.BigDecimal amountMax,
            String search, int page, int size, String sortBy, String sortDir) {

        BankTransactionPageDto result = bankMatchingService.getUnmatchedTransactions(
                entityId, bankAccountId, dateFrom, dateTo,
                amountMin, amountMax, search, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success(200, "Unmatched transactions retrieved successfully", result));
    }

    // =========================================================================
    // POST /transactions/{id}/categorize (LLR-BNK-03.2)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<com.af.novadesk.api.finance.dto.ExpenseTransactionDto>> categorizeTransaction(
            UUID transactionId, CategorizeTransactionRequest request) {
        var result = categorizationService.categorizeTransaction(transactionId, request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Transaction categorized successfully", result));
    }

    // =========================================================================
    // POST /transactions/bulk-categorize (LLR-BNK-03.4)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<java.util.List<com.af.novadesk.api.finance.dto.ExpenseTransactionDto>>> bulkCategorize(
            BulkCategorizeRequest request) {
        var result = categorizationService.bulkCategorize(request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(201, result.size() + " transactions categorized successfully", result));
    }

    // =========================================================================
    // POST /transactions/{id}/split (LLR-BNK-03.5)
    // =========================================================================

    @Override
    public ResponseEntity<ApiResponse<java.util.List<com.af.novadesk.api.finance.dto.ExpenseTransactionDto>>> splitTransaction(
            UUID transactionId, SplitTransactionRequest request) {
        var result = categorizationService.splitTransaction(transactionId, request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(201, result.size() + " expense transactions created from split", result));
    }
}
