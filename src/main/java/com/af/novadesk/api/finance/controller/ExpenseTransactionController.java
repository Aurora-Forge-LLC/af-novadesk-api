package com.af.novadesk.api.finance.controller;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.api.ExpenseTransactionApi;
import com.af.novadesk.api.finance.dto.ExpenseAttachmentDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionPageDto;
import com.af.novadesk.api.finance.dto.VoidExpenseDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * Stub controller for {@link ExpenseTransactionApi}.
 *
 * <p>Routes are registered and visible in Swagger UI.
 * Service-layer implementation is pending (LLR-FIN-03).</p>
 */
@RestController
public class ExpenseTransactionController implements ExpenseTransactionApi {

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionDto>> recordExpense(ExpenseTransactionDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionPageDto>> listExpenses(int page, int size, String sortBy, String status) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionDto>> getExpense(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseTransactionDto>> voidExpense(UUID id, VoidExpenseDto request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<ExpenseAttachmentDto>> uploadAttachment(UUID id, MultipartFile file) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<List<ExpenseAttachmentDto>>> listAttachments(UUID id) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public ResponseEntity<ApiResponse<Void>> deleteAttachment(UUID transactionId, UUID attachmentId) {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
