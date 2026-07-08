package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.ExpenseTransactionApi;
import com.af.novadesk.api.finance.constants.PaymentMethod;
import com.af.novadesk.api.finance.dto.ExpenseAttachmentDto;
import com.af.novadesk.api.finance.dto.ExpenseLedgerResponse;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionPageDto;
import com.af.novadesk.api.finance.dto.VoidExpenseDto;
import com.af.novadesk.api.finance.service.ExpenseTransactionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Manual Expense Recording (LLR-FIN-03).
 *
 * <p>Implements {@link ExpenseTransactionApi} — all routing, security, and Swagger
 * annotations are declared on the interface. This class only wires the service calls.</p>
 */
@RestController
@RequiredArgsConstructor
public class ExpenseTransactionController implements ExpenseTransactionApi {

    private final ExpenseTransactionService expenseTransactionService;

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionDto>> recordExpense(ExpenseTransactionDto request) {
        ExpenseTransactionDto created = expenseTransactionService.recordExpense(request);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Expense recorded successfully", created));
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionPageDto>> listExpenses(
            int page, int size, String sortBy, String sortDir,
            String q, String status,
            UUID legalEntityId, UUID vendorId,
            PaymentMethod paymentMethod,
            LocalDate fromDate, LocalDate toDate,
            BigDecimal minAmount, BigDecimal maxAmount,
            String reconciliationStatus) {
        ExpenseTransactionPageDto result = expenseTransactionService.listExpenses(
                page, size, sortBy, sortDir, q, status,
                legalEntityId, vendorId, paymentMethod,
                fromDate, toDate, minAmount, maxAmount, reconciliationStatus);
        return ResponseEntity.ok(ApiResponse.success(200, "Expenses retrieved successfully", result));
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionDto>> getExpense(UUID id) {
        ExpenseTransactionDto expense = expenseTransactionService.getExpense(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Expense retrieved successfully", expense));
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionDto>> voidExpense(UUID id, VoidExpenseDto request) {
        ExpenseTransactionDto voided = expenseTransactionService.voidExpense(id, request);
        return ResponseEntity.ok(ApiResponse.success(200, "Expense voided successfully", voided));
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseLedgerResponse>> getExpenseLedger(UUID id) {
        ExpenseLedgerResponse ledger = expenseTransactionService.getExpenseLedger(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Ledger entries retrieved successfully", ledger));
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseAttachmentDto>> uploadAttachment(UUID id, MultipartFile file) {
        ExpenseAttachmentDto attachment = expenseTransactionService.uploadAttachment(id, file);
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success(201, "Attachment uploaded successfully", attachment));
    }

    @Override
    public ResponseEntity<ApiResponse<List<ExpenseAttachmentDto>>> listAttachments(UUID id) {
        List<ExpenseAttachmentDto> attachments = expenseTransactionService.listAttachments(id);
        return ResponseEntity.ok(ApiResponse.success(200, "Attachments retrieved successfully", attachments));
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(UUID transactionId, UUID attachmentId) {
        expenseTransactionService.deleteAttachment(transactionId, attachmentId);
        return ResponseEntity
                .status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.successEmpty(204, "Attachment deleted successfully"));
    }
}
