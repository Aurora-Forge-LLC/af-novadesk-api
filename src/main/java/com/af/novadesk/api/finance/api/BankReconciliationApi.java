package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.BankStatementUploadRequest;
import com.af.novadesk.api.finance.dto.BankTransactionPageDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * REST API contract for Bank Reconciliation (LLR-BNK-01/02/03/04).
 *
 * <p>Base path: {@code /api/v1/finance/bank-reconciliation}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in {@link ApiResponse} for a consistent envelope.</p>
 */
@Tag(name = "Bank Reconciliation", description = "Upload bank statements, list transactions, manage reconciliation — LLR-BNK-01/02/03/04")
@RequestMapping("/api/v1/finance/bank-reconciliation")
@SecurityRequirement(name = "bearerAuth")
public interface BankReconciliationApi {

    // =========================================================================
    // LLR-BNK-01.1: Upload Statement
    // =========================================================================

    /**
     * POST /api/v1/finance/bank-reconciliation/upload
     * Uploads a bank statement file and extracts transactions.
     */
    @Operation(
            summary     = "Upload bank statement",
            description = "Accepts a multipart upload containing the statement file (CSV, XLSX) " +
                          "and form fields (entity, bank account, period, optional password/notes). " +
                          "Validates entity/bank-account ownership, checks for duplicates, " +
                          "stores the file in object storage, and parses transactions. " +
                          "If a duplicate period is detected, a CONFLICT with existing statement " +
                          "details is returned — use the replace endpoint to supersede (LLR-BNK-01.1)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Statement uploaded and transactions extracted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error — missing fields, unsupported file type, file too large"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Entity or bank account not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate — overlapping statement exists for this bank account + period"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "Statement file is malformed and could not be parsed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping(value = "/upload", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<BankStatementDto>> uploadStatement(
            @Parameter(description = "JSON string of form fields: {\"entityId\":\"...\",\"bankAccountId\":\"...\",\"periodStart\":\"yyyy-MM-dd\",\"periodEnd\":\"yyyy-MM-dd\",\"filePassword\":\"...\",\"notes\":\"...\"}. Must be JSON.stringify()'d on frontend.")
            @RequestParam("request") String requestJson,

            @Parameter(description = "Bank statement file (CSV or XLSX, max 15 MB)")
            @RequestPart("file") MultipartFile file);

    /**
     * POST /api/v1/finance/bank-reconciliation/replace
     * Supersedes overlapping statements and uploads a new one.
     */
    @Operation(
            summary     = "Replace bank statement",
            description = "Supersedes all existing active statements for the same " +
                          "bank account + period, then uploads and parses the new file. " +
                          "Use this endpoint when the upload endpoint returns a 409 conflict " +
                          "and the user wants to replace the old statement (LLR-BNK-01.1)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Previous statements superseded; new statement uploaded"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Insufficient permissions")
    })
    @PostMapping(value = "/replace", consumes = "multipart/form-data")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<BankStatementDto>> replaceStatement(
            @Parameter(description = "JSON string of form fields: {\"entityId\":\"...\",\"bankAccountId\":\"...\",\"periodStart\":\"yyyy-MM-dd\",\"periodEnd\":\"yyyy-MM-dd\",\"filePassword\":\"...\",\"notes\":\"...\"}. Must be JSON.stringify()'d on frontend.")
            @RequestParam("request") String requestJson,

            @Parameter(description = "Replacement bank statement file (CSV or XLSX, max 15 MB)")
            @RequestPart("file") MultipartFile file);

    // =========================================================================
    // LLR-BNK-01: Statement list & detail
    // =========================================================================

    /**
     * GET /api/v1/finance/bank-reconciliation/statements
     * Lists all bank statements for a legal entity (paginated).
     */
    @Operation(
            summary     = "List bank statements",
            description = "Returns all bank statements for the given legal entity, " +
                          "ordered by upload date descending (paginated)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Statements retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Legal entity not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/statements")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<BankStatementPageDto>> listStatements(
            @Parameter(description = "Legal entity UUID")
            @RequestParam UUID legalEntityId,

            @Parameter(description = "Page number (0-based)")
            @RequestParam(defaultValue = "0") int page,

            @Parameter(description = "Page size")
            @RequestParam(defaultValue = "20") int size);

    /**
     * GET /api/v1/finance/bank-reconciliation/statements/{id}
     * Returns full detail for a single bank statement.
     */
    @Operation(
            summary     = "Get bank statement by ID",
            description = "Retrieves a single bank statement with its metadata by UUID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Statement retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Statement not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/statements/{id}")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<BankStatementDto>> getStatement(
            @Parameter(description = "Statement UUID") @PathVariable UUID id);

    // =========================================================================
    // LLR-BNK-01: Transaction list
    // =========================================================================

    /**
     * GET /api/v1/finance/bank-reconciliation/statements/{statementId}/transactions
     * Lists all transactions for a statement (paginated).
     */
    @Operation(
            summary     = "List bank transactions",
            description = "Returns all transactions extracted from the given statement, " +
                          "ordered by transaction date ascending (paginated)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Statement not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/statements/{statementId}/transactions")
    @PreAuthorize("hasAuthority('organizations:read')")
    ResponseEntity<ApiResponse<BankTransactionPageDto>> listTransactions(
            @Parameter(description = "Statement UUID") @PathVariable UUID statementId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size")              @RequestParam(defaultValue = "20") int size);
}
