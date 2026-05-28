package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.ExpenseAttachmentDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.dto.ExpenseTransactionPageDto;
import com.af.novadesk.api.finance.dto.VoidExpenseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Manual Expense Recording (LLR-FIN-03).
 *
 * <p>Base path: {@code /api/v1/expense/transactions}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in {@link ApiResponse} for a consistent envelope.</p>
 *
 * <p>On every successful {@code POST /}, the service creates exactly two
 * {@code fa_ledger_entries} rows in the same transaction:
 * one CREDIT on the source account and one DEBIT on the destination account
 * (LLR-FIN-03.2).</p>
 */
@Tag(name = "Expense Transactions", description = "Manual expense recording and attachment management — LLR-FIN-03")
@RequestMapping("/api/v1/expense/transactions")
@SecurityRequirement(name = "bearerAuth")
public interface ExpenseTransactionApi {

    // =========================================================================
    // LLR-FIN-03.1 / 03.2: Expense Entry & Double-Entry Enforcement
    // =========================================================================

    /**
     * POST /api/v1/expense/transactions
     * Records a new expense and posts the double-entry ledger entries.
     */
    @Operation(
            summary     = "Record expense",
            description = "Records a new manual expense against the caller's active legal entity. " +
                          "The system validates that source ≠ destination account and " +
                          "creates two balanced ledger entries (CREDIT + DEBIT) in the same " +
                          "transaction (LLR-FIN-03.2). Returns the saved expense with its ID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Expense recorded and ledger entries posted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — including source == destination account"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Vendor or account not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    ResponseEntity<ApiResponse<ExpenseTransactionDto>> recordExpense(
            @Valid @RequestBody ExpenseTransactionDto request);

    /**
     * GET /api/v1/expense/transactions
     * Lists all expenses for the caller's active legal entity (paginated).
     */
    @Operation(
            summary     = "List expenses",
            description = "Returns a paginated list of all expense transactions for the caller's " +
                          "active legal entity. Supports optional filtering by transaction status."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expenses retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    ResponseEntity<ApiResponse<ExpenseTransactionPageDto>> listExpenses(
            @Parameter(description = "Page number (0-based)")    @RequestParam(defaultValue = "0")             int    page,
            @Parameter(description = "Page size")                @RequestParam(defaultValue = "20")            int    size,
            @Parameter(description = "Sort field")               @RequestParam(defaultValue = "expenseDate")   String sortBy,
            @Parameter(description = "Filter by transaction status: POSTED or VOID (optional)")
                                                                 @RequestParam(required = false)               String status);

    /**
     * GET /api/v1/expense/transactions/{id}
     * Returns full detail for a single expense transaction.
     */
    @Operation(
            summary     = "Get expense by ID",
            description = "Retrieves a single expense transaction by UUID. " +
                          "Scoped to the caller's active legal entity."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expense retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('EXPENSE_READ')")
    ResponseEntity<ApiResponse<ExpenseTransactionDto>> getExpense(
            @Parameter(description = "Expense transaction UUID") @PathVariable UUID id);

    /**
     * POST /api/v1/expense/transactions/{id}/void
     * Voids a posted expense and posts offsetting reversal ledger entries.
     */
    @Operation(
            summary     = "Void expense",
            description = "Cancels a POSTED expense by creating two offsetting reversal ledger entries " +
                          "and marking the transaction as VOID. A voided transaction is immutable — " +
                          "it cannot be reinstated. Only POSTED transactions can be voided."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Expense voided and reversal entries posted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Transaction is already VOID"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping("/{id}/void")
    @PreAuthorize("hasAuthority('EXPENSE_VOID')")
    ResponseEntity<ApiResponse<ExpenseTransactionDto>> voidExpense(
            @Parameter(description = "Expense transaction UUID") @PathVariable UUID id,
            @Valid @RequestBody VoidExpenseDto request);

    // =========================================================================
    // LLR-FIN-03.4: Attachment Management
    // =========================================================================

    /**
     * POST /api/v1/expense/transactions/{id}/attachments
     * Uploads and attaches an invoice or receipt to an expense transaction.
     */
    @Operation(
            summary     = "Upload attachment",
            description = "Encrypts and stores a file (PDF, PNG, JPG, JPEG; max 5 MB) in object storage " +
                          "and links it to the expense transaction. Only users with " +
                          "VIEW_FINANCIAL_DOCUMENTS permission can later retrieve it (LLR-FIN-03.4)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Attachment uploaded and linked successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Unsupported file type or file exceeds 5 MB"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense transaction not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    ResponseEntity<ApiResponse<ExpenseAttachmentDto>> uploadAttachment(
            @Parameter(description = "Expense transaction UUID") @PathVariable UUID id,
            @Parameter(description = "File to upload (PDF, PNG, JPG, JPEG — max 5 MB)")
            @RequestParam("file") MultipartFile file);

    /**
     * GET /api/v1/expense/transactions/{id}/attachments
     * Lists all attachment metadata for an expense transaction.
     */
    @Operation(
            summary     = "List attachments",
            description = "Returns metadata for all files attached to the given expense transaction. " +
                          "Requires VIEW_FINANCIAL_DOCUMENTS permission (LLR-FIN-03.4). " +
                          "Does not return file content — use the pre-signed URL from each record to download."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Attachments retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Expense transaction not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @GetMapping("/{id}/attachments")
    @PreAuthorize("hasAuthority('VIEW_FINANCIAL_DOCUMENTS')")
    ResponseEntity<ApiResponse<List<ExpenseAttachmentDto>>> listAttachments(
            @Parameter(description = "Expense transaction UUID") @PathVariable UUID id);

    /**
     * DELETE /api/v1/expense/transactions/{transactionId}/attachments/{attachmentId}
     * Removes an attachment from an expense transaction and deletes it from object storage.
     */
    @Operation(
            summary     = "Delete attachment",
            description = "Removes the attachment record from the database and deletes the object " +
                          "from storage. Only allowed on POSTED (non-voided) transactions."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Attachment deleted successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Transaction or attachment not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Cannot delete attachment on a VOID transaction"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @DeleteMapping("/{transactionId}/attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('EXPENSE_CREATE')")
    ResponseEntity<ApiResponse<Void>> deleteAttachment(
            @Parameter(description = "Expense transaction UUID") @PathVariable UUID transactionId,
            @Parameter(description = "Attachment UUID")          @PathVariable UUID attachmentId);
}
