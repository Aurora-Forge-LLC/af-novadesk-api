package com.af.novadesk.api.finance.funding.api;

import com.af.novadesk.api.finance.funding.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.funding.dto.CapitalInjectionResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;

@Tag(name = "Capital Injection", description = "Decentralised funding and capital injection endpoints")
@RequestMapping("/api/v1/finance/funding")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
public interface CapitalInjectionApi {

    @Operation(
            summary = "Record a capital injection",
            description = "Creates a balanced double-entry posting for a funding event. " +
                    "For inter-entity transfers, postings are written across both entity ledgers."
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "201",
                    description = "Capital injection recorded successfully",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = com.af.novadesk.api.common.response.ApiResponse.class),
                            examples = @ExampleObject(name = "success", value = """
                                    {
                                      "success": true,
                                      "code": 201,
                                      "message": "Capital injection recorded successfully",
                                      "data": {
                                        "capital_injection_id": "550e8400-e29b-41d4-a716-446655440000",
                                        "journal_id": "6f41e3c3-8af7-4c52-a6f1-2d85a091a89b",
                                        "transfer_id": null,
                                        "target_entity_code": "INDIA",
                                        "source_entity_code": null,
                                        "amount_local": 100000.0000,
                                        "currency_local": "INR",
                                        "amount_usd": 1200.0000,
                                        "exchange_rate_used": 0.012000,
                                        "rate_date_used": "2026-05-18",
                                        "rate_source": "API",
                                        "message": "Capital injection created and posted to ledger"
                                      },
                                      "timestamp": "2026-05-18T14:30:45.123Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(name = "validation_error", value = """
                                    {
                                      "success": false,
                                      "code": 400,
                                      "message": "Validation failed. Please check the errors and try again",
                                      "data": null,
                                      "metadata": {
                                        "errors": [
                                          {"field": "amount", "message": "Amount must be at least 0.01", "code": "DecimalMin"}
                                        ]
                                      },
                                      "timestamp": "2026-05-18T14:31:00.123Z"
                                    }
                                    """)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Not authenticated"),
            @ApiResponse(responseCode = "404", description = "Entity or account not found"),
            @ApiResponse(responseCode = "422", description = "Exchange rate unavailable and manual rate not provided"),
            @ApiResponse(responseCode = "500", description = "Unexpected server error")
    })
    @PostMapping("/capital-injections")
    ResponseEntity<com.af.novadesk.api.common.response.ApiResponse<CapitalInjectionResponse>> createCapitalInjection(
            @Valid @RequestBody CapitalInjectionRequest request
    );
}

