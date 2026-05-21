package com.af.novadesk.api.finance.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.finance.dto.ExchangeRateSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * REST API contract for Exchange Rate reference endpoints.
 *
 * <p>Base path: {@code /api/v1/finance/exchange-rates}</p>
 *
 * <p>All endpoints require a valid JWT bearer token. Every response is wrapped
 * in the standard {@link ApiResponse} envelope.</p>
 */
@Tag(name = "Exchange Rates", description = "Exchange-rate reference endpoints")
@RequestMapping("/api/v1/finance/exchange-rates")
@SecurityRequirement(name = "bearerAuth")
public interface ExchangeRateApi {

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
                                          "sourceCurrency": "USD",
                                          "targetCurrency": "EUR",
                                          "rateDate": "2026-05-20",
                                          "exchangeRate": 0.9200,
                                          "rateSource": "ECB",
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
                                        "sourceCurrency": "USD",
                                        "targetCurrency": "EUR",
                                        "rateDate": "2026-05-20",
                                        "exchangeRate": 0.9200,
                                        "rateSource": "ECB",
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
    ResponseEntity<ApiResponse<ExchangeRateSummaryResponse>> getById(
            @Parameter(description = "Exchange-rate UUID") @PathVariable UUID id
    );
}
