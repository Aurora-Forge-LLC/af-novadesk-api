package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.LedgerReportResponse;
import com.af.novadesk.api.finance.dto.MultiEntityConsolidatedReport;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST API contract for financial ledger report endpoints (LLR-FIN-04.5).
 *
 * <p>Base path: {@code /api/v1/finance/reports}</p>
 */
@Tag(name = "Financial Reports", description = "Ledger report and consolidated financial endpoints")
@RequestMapping("/api/v1/finance/reports")
@SecurityRequirement(name = "bearerAuth")
public interface LedgerReportApi {

    /**
     * GET /api/v1/finance/reports/ledger
     * Single-entity ledger report with currency selector.
     */
    @Operation(
            summary = "Generate ledger report",
            description = "Generates a paginated ledger report for a single legal entity. " +
                    "Supports currency selection: 'USD' (default) uses the USD-amount column, " +
                    "'LOCAL' uses the entity's base-currency amount column."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Report generated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Entity not found")
    })
    @GetMapping("/ledger")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<LedgerReportResponse>> getLedgerReport(
            @Parameter(description = "Legal entity UUID", required = true) @RequestParam UUID entityId,
            @Parameter(description = "Start date (inclusive)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (inclusive)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "Currency: 'USD' or 'LOCAL'", example = "USD") @RequestParam(defaultValue = "USD") String currency,
            @Parameter(description = "Filter by account UUID") @RequestParam(required = false) UUID accountId,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size
    );

    /**
     * GET /api/v1/finance/reports/consolidated
     * Multi-entity consolidated report (always in USD).
     */
    @Operation(
            summary = "Generate consolidated report",
            description = "Generates a multi-entity consolidated financial report. " +
                    "All amounts are in USD for cross-entity comparability (LLR-FIN-04.5)."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Consolidated report generated successfully",
                    content = @Content(schema = @Schema(implementation = ApiResponse.class))
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid parameters"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/consolidated")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<MultiEntityConsolidatedReport>> getConsolidatedReport(
            @Parameter(description = "List of legal entity UUIDs", required = true) @RequestParam List<UUID> entityIds,
            @Parameter(description = "Start date (inclusive)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "End date (inclusive)") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    );
}
