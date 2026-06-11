package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankStatementPageDto;
import com.af.novadesk.api.finance.dto.DuplicateStatementWarningDto;
import com.af.novadesk.api.finance.dto.ResolveSuggestionRequest;
import com.af.novadesk.api.finance.dto.SuggestedMatchDto;
import com.af.novadesk.api.finance.dto.SuggestedMatchPageDto;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

/**
 * REST API interface for Bank Reconciliation — Upload Interface (LLR-BNK-01.1).
 *
 * <p>Endpoints defined:</p>
 * <ul>
 *   <li>{@code POST /api/v1/bank-reconciliation/statements/upload} — Upload a statement</li>
 *   <li>{@code POST /api/v1/bank-reconciliation/statements/{id}/parse} — Parse uploaded statement</li>
 *   <li>{@code POST /api/v1/bank-reconciliation/statements/{id}/replace} — Replace existing statement</li>
 *   <li>{@code GET /api/v1/bank-reconciliation/statements/{id}} — Get statement details</li>
 *   <li>{@code GET /api/v1/bank-reconciliation/statements} — List statements (paginated)</li>
 *   <li>{@code GET /api/v1/bank-reconciliation/statements/check-duplicate} — Check for duplicates</li>
 * </ul>
 */
@Tag(name = "Bank Reconciliation", description = "Bank statement upload and reconciliation — LLR-BNK-01")
@RequestMapping("/api/v1/bank-reconciliation/statements")
public interface BankReconciliationApi {

    // -------------------------------------------------------------------------
    // POST /upload
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Upload bank statement",
            description = """
                    Upload a bank statement file (CSV, XLSX, XLS) for reconciliation.
                    The file is validated, stored, and automatically parsed to extract transactions.
                    Send all fields as individual form-data parameters (NOT as a JSON string).

                    JavaScript/TypeScript Example:
                    const formData = new FormData();
                    formData.append('entityId', '12345678-1234-1234-1234-123456789012');
                    formData.append('bankAccountId', '87654321-4321-4321-4321-210987654321');
                    formData.append('periodStart', '2026-01-01');
                    formData.append('periodEnd', '2026-01-31');
                    formData.append('filePassword', '');
                    formData.append('notes', 'January reconciliation');
                    formData.append('file', fileInputElement.files[0]);
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Statement uploaded and parsed successfully",
                    content = @Content(schema = @Schema(implementation = BankStatementDto.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error (invalid file type, missing fields, duplicate period)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "File parsing failed (malformed CSV/Excel)"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "413",
                    description = "File too large"
            )
    })
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<BankStatementDto>> uploadStatement(
            @Parameter(description = "Legal entity UUID", required = true)
            @RequestParam("entityId") UUID entityId,

            @Parameter(description = "Bank account UUID", required = true)
            @RequestParam("bankAccountId") UUID bankAccountId,

            @Parameter(description = "Statement period start date (YYYY-MM-DD)", required = true)
            @RequestParam("periodStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,

            @Parameter(description = "Statement period end date (YYYY-MM-DD)", required = true)
            @RequestParam("periodEnd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,

            @Parameter(description = "Optional password for encrypted Excel files")
            @RequestParam(value = "filePassword", required = false) String filePassword,

            @Parameter(description = "Optional notes (max 500 characters)")
            @RequestParam(value = "notes", required = false) String notes,

            @Parameter(description = "Bank statement file (CSV, XLSX, XLS)")
            @RequestPart("file") MultipartFile file
    );

    // -------------------------------------------------------------------------
    // POST /{id}/parse
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Parse uploaded statement",
            description = """
                    Parse a previously uploaded bank statement file that is in UPLOADED or FAILED status.
                    Extracts transactions from the stored file and updates the statement.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Statement parsed successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Statement not found"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "422",
                    description = "Statement not in a parseable state"
            )
    })
    @PostMapping("/{id}/parse")
    ResponseEntity<ApiResponse<BankStatementDto>> parseStatement(
            @Parameter(description = "Statement UUID")
            @PathVariable("id") UUID statementId
    );

    // -------------------------------------------------------------------------
    // POST /{id}/replace
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Replace existing statement",
            description = """
                    Replace an existing bank statement with a new file upload.
                    The existing statement is marked as SUPERSEDED and a new statement is created.
                    Send all fields as individual form-data parameters (NOT as a JSON string).

                    JavaScript/TypeScript Example:
                    const formData = new FormData();
                    formData.append('entityId', '12345678-1234-1234-1234-123456789012');
                    formData.append('bankAccountId', '87654321-4321-4321-4321-210987654321');
                    formData.append('periodStart', '2026-01-01');
                    formData.append('periodEnd', '2026-01-31');
                    formData.append('file', fileInputElement.files[0]);
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "201",
                    description = "Replacement statement created successfully"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "400",
                    description = "Validation error"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Existing statement not found"
            )
    })
    @PostMapping(value = "/{id}/replace", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ApiResponse<BankStatementDto>> replaceStatement(
            @Parameter(description = "UUID of the statement to replace")
            @PathVariable("id") UUID existingStatementId,

            @Parameter(description = "Legal entity UUID", required = true)
            @RequestParam("entityId") UUID entityId,

            @Parameter(description = "Bank account UUID", required = true)
            @RequestParam("bankAccountId") UUID bankAccountId,

            @Parameter(description = "Statement period start date (YYYY-MM-DD)", required = true)
            @RequestParam("periodStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,

            @Parameter(description = "Statement period end date (YYYY-MM-DD)", required = true)
            @RequestParam("periodEnd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,

            @Parameter(description = "Optional password for encrypted Excel files")
            @RequestParam(value = "filePassword", required = false) String filePassword,

            @Parameter(description = "Optional notes (max 500 characters)")
            @RequestParam(value = "notes", required = false) String notes,

            @Parameter(description = "New bank statement file")
            @RequestPart("file") MultipartFile file
    );

    // -------------------------------------------------------------------------
    // GET /check-duplicate
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Check for duplicate statement",
            description = """
                    Check if a statement already exists for the given bank account and period.
                    Returns warning details if a duplicate is found, or an empty response if not.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Duplicate check completed (data is null if no duplicate)"
            )
    })
    @GetMapping("/check-duplicate")
    ResponseEntity<ApiResponse<DuplicateStatementWarningDto>> checkDuplicate(
            @Parameter(description = "Bank account UUID", required = true)
            @RequestParam("bankAccountId") UUID bankAccountId,

            @Parameter(description = "Statement period start date", required = true)
            @RequestParam("periodStart") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,

            @Parameter(description = "Statement period end date", required = true)
            @RequestParam("periodEnd") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd
    );

    // -------------------------------------------------------------------------
    // GET /{id}
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Get bank statement details",
            description = "Retrieve full details of a previously uploaded bank statement."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Statement found"
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Statement not found"
            )
    })
    @GetMapping("/{id}")
    ResponseEntity<ApiResponse<BankStatementDto>> getStatement(
            @Parameter(description = "Statement UUID")
            @PathVariable("id") UUID statementId
    );

    // -------------------------------------------------------------------------
    // GET /
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List bank statements",
            description = """
                    Paginated list of bank statements for a given legal entity.
                    Filter by entity ID query parameter.
                    """
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "List of statements"
            )
    })
    @GetMapping
    ResponseEntity<ApiResponse<BankStatementPageDto>> listStatements(
            @Parameter(description = "Legal entity UUID to filter by")
            @RequestParam("entityId") UUID legalEntityId,

            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size
    );

    // -------------------------------------------------------------------------
    // GET /suggested-matches (LLR-BNK-02.4)
    // -------------------------------------------------------------------------

    @Operation(
            summary = "List suggested matches for review",
            description = "Paginated list of medium-confidence (score 60-79) match suggestions pending user review."
    )
    @GetMapping("/suggested-matches")
    ResponseEntity<ApiResponse<SuggestedMatchPageDto>> getSuggestedMatches(
            @Parameter(description = "Zero-based page number", example = "0")
            @RequestParam(value = "page", defaultValue = "0") int page,

            @Parameter(description = "Page size", example = "20")
            @RequestParam(value = "size", defaultValue = "20") int size
    );

    // -------------------------------------------------------------------------
    // POST /suggested-matches/{id}/resolve (LLR-BNK-02.4)
    // -------------------------------------------------------------------------

    @Operation(
            summary = "Accept or reject a suggested match",
            description = "Accept to confirm the match and update both records, or reject to reset the bank transaction to UNMATCHED."
    )
    @PostMapping("/suggested-matches/{id}/resolve")
    ResponseEntity<ApiResponse<SuggestedMatchDto>> resolveSuggestion(
            @Parameter(description = "Suggested match UUID")
            @PathVariable("id") UUID suggestionId,

            @Parameter(description = "Action: ACCEPT or REJECT")
            @Valid @RequestBody ResolveSuggestionRequest request
    );
}
