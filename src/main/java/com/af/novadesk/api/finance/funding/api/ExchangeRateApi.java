package com.af.novadesk.api.finance.funding.api;

import com.af.novadesk.api.finance.funding.dto.ExchangeRateSummaryResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "Exchange Rates", description = "Exchange-rate reference endpoints")
@RequestMapping("/api/v1/finance/exchange-rates")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public interface ExchangeRateApi {

    @Operation(summary = "List exchange rates", description = "Retrieve exchange rates, optionally filtered by pair/date")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange rates retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = com.af.novadesk.api.common.response.ApiResponse.class))),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @GetMapping
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<List<ExchangeRateSummaryResponse>>> list(
            @Parameter(description = "Source currency (ISO-4217)") @RequestParam(required = false) String sourceCurrency,
            @Parameter(description = "Target currency (ISO-4217)") @RequestParam(required = false) String targetCurrency,
            @Parameter(description = "Exact rate date") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate rateDate
    );

    @Operation(summary = "Get exchange rate by ID", description = "Retrieve one exchange-rate record by UUID")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Exchange rate retrieved successfully"),
            @ApiResponse(responseCode = "404", description = "Exchange rate not found"),
            @ApiResponse(responseCode = "401", description = "Not authenticated")
    })
    @GetMapping("/{id}")
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<ExchangeRateSummaryResponse>> getById(
            @Parameter(description = "Exchange-rate UUID") @PathVariable UUID id
    );
}

