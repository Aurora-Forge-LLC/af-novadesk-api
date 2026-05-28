package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.CsvUploadResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateDetailResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateRequest;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Exchange Rate endpoints (LLR-FIN-04).
 *
 * <p>Base path: {@code /api/v1/finance/exchange-rates}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in the standard {@link ApiResponse} envelope.</p>
 */
@Tag(name = "Exchange Rates", description = "Exchange-rate management and reference endpoints")
@RequestMapping("/api/v1/finance/exchange-rates")
@SecurityRequirement(name = "bearerAuth")
public interface ExchangeRateApi {

    // =========================================================================
    // Read endpoints
    // =========================================================================

    /**
     * GET /api/v1/finance/exchange-rates
     * Lists exchange rates, optionally filtered by currency pair and/or date.
     */
    @Operation(
            summary = "List exchange rates",
            description = "Retrieves exchange rates, optionally filtered by source currency, " +
                    "target currency, and/or rate date. All filters are independent — partial " +
                    "filter sets are honoured correctly without falling back to an unfiltered scan."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Exchange rates retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Records retrieved successfully",
                                      "data": [
                                        {
                                          "id": "00000000-0000-0000-0000-000000000001",
                                          "sourceCurrency": "INR",
                                          "targetCurrency": "USD",
                                          "rateDate": "2026-05-20",
                                          "exchangeRate": 0.012045,
                                          "rateSource": "MANUAL",
                                          "status": "ACTIVE",
                                          "createdAt": "2026-05-20T10:00:00"
                                        }
                                      ],
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<List<ExchangeRateSummaryResponse>>> list(
            @Parameter(description = "Source currency (ISO-4217)") @RequestParam(required = false) String sourceCurrency,
            @Parameter(description = "Target currency (ISO-4217)") @RequestParam(required = false) String targetCurrency,
            @Parameter(description = "Exact rate date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rateDate
    );

    /**
     * GET /api/v1/finance/exchange-rates/{id}
     * Returns a single exchange-rate record by UUID.
     */
    @Operation(
            summary = "Get exchange rate by ID",
            description = "Retrieves one exchange-rate record by its UUID."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "200",
                    description = "Exchange rate retrieved successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 200,
                                      "message": "Record retrieved successfully",
                                      "data": {
                                        "id": "00000000-0000-0000-0000-000000000001",
                                        "sourceCurrency": "INR",
                                        "targetCurrency": "USD",
                                        "rateDate": "2026-05-20",
                                        "exchangeRate": 0.012045,
                                        "rateSource": "MANUAL",
                                        "status": "ACTIVE",
                                        "createdAt": "2026-05-20T10:00:00"
                                      },
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(
                    responseCode = "404",
                    description = "Exchange rate not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "not_found", value = """
                                    {
                                      "success": false,
                                      "code": 404,
                                      "message": "Exchange rate not found with id: 00000000-0000-0000-0000-000000000999",
                                      "data": null,
                                      "timestamp": "2026-05-20T10:00:00.123Z"
                                    }
                                    """)
                    )
            ),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> getById(
            @Parameter(description = "Exchange-rate UUID") @PathVariable UUID id
    );

    // =========================================================================
    // Write endpoints (LLR-FIN-04.1)
    // =========================================================================

    /**
     * POST /api/v1/finance/exchange-rates
     * Creates a new manual exchange rate.
     */
    @Operation(
            summary = "Create exchange rate",
            description = "Creates a new manual exchange rate. Rate source is always MANUAL for admin-entered rates."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Exchange rate created"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Duplicate rate exists"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> create(
            @Valid @RequestBody ExchangeRateRequest request
    );

    /**
     * PUT /api/v1/finance/exchange-rates/{id}
     * Updates an existing exchange rate.
     */
    @Operation(
            summary = "Update exchange rate",
            description = "Updates rate value and date for an existing exchange rate. Currency pair is immutable."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exchange rate updated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Validation error"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Rate not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<ExchangeRateDetailResponse>> update(
            @Parameter(description = "Exchange-rate UUID") @PathVariable UUID id,
            @Valid @RequestBody ExchangeRateRequest request
    );

    /**
     * PATCH /api/v1/finance/exchange-rates/{id}/approve
     * Approves a manual exchange rate (LLR-FIN-04.1).
     */
    @Operation(
            summary = "Approve exchange rate",
            description = "Records the approver for a manually-entered exchange rate."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exchange rate approved"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Rate not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PatchMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> approve(
            @Parameter(description = "Exchange-rate UUID") @PathVariable UUID id
    );

    /**
     * DELETE /api/v1/finance/exchange-rates/{id}
     * Soft-deletes an exchange rate.
     */
    @Operation(
            summary = "Delete exchange rate",
            description = "Soft-deletes an exchange rate by setting its status to INACTIVE."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Exchange rate deleted"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Rate not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<Void>> delete(
            @Parameter(description = "Exchange-rate UUID") @PathVariable UUID id
    );

    // =========================================================================
    // CSV Upload (LLR-FIN-04.2)
    // =========================================================================

    /**
     * POST /api/v1/finance/exchange-rates/csv-upload
     * Uploads a CSV file containing exchange rates for air-gapped deployments.
     *
     * <p>CSV format: {@code date, currency_pair, rate}
     * <br>Example: {@code 2026-04-23, INR-USD, 0.012045}</p>
     */
    @Operation(
            summary = "Upload exchange rates via CSV",
            description = "Imports exchange rates from a CSV file. Format: date, currency_pair, rate. " +
                    "Duplicate rows (same source+target+date) are skipped. Validation errors are reported per-row."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "CSV processed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid file format"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "422", description = "CSV parsing failed"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @PostMapping(value = "/csv-upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('organizations:write')")
    ResponseEntity<ApiResponse<CsvUploadResponse>> uploadCsv(
            @Parameter(description = "CSV file to upload", required = true)
            @RequestPart("file") MultipartFile file
    );
}
