package com.af.novadesk.api.finance.funding.controller;

import com.af.novadesk.api.finance.funding.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.funding.dto.CapitalInjectionResponse;
import com.af.novadesk.api.finance.funding.service.CapitalInjectionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for capital-injection operations (LLR-FIN-02).
 *
 * <p>Base path: {@code /api/v1/finance/funding}</p>
 */
@RestController
@RequestMapping("/api/v1/finance/funding")
@Tag(name = "Capital Injection", description = "Decentralised funding & capital injection (LLR-FIN-02)")
public class CapitalInjectionController {

    private final CapitalInjectionService capitalInjectionService;

    public CapitalInjectionController(CapitalInjectionService capitalInjectionService) {
        this.capitalInjectionService = capitalInjectionService;
    }

    /**
     * Records a capital injection and its double-entry ledger postings.
     *
     * @param request validated injection request.
     * @return response containing the created record IDs and amount summary.
     */
    @PostMapping("/capital-injections")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "Record a capital injection",
            description = "Creates a balanced double-entry journal for a funding event. "
                    + "For inter-entity transfers, four ledger legs are created across both entity ledgers.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Injection recorded successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid input or unbalanced entries"),
                    @ApiResponse(responseCode = "404", description = "Entity or account not found"),
                    @ApiResponse(responseCode = "422", description = "Missing exchange rate — manual rate required")
            }
    )
    public CapitalInjectionResponse createCapitalInjection(
            @Valid @RequestBody CapitalInjectionRequest request
    ) {
        return capitalInjectionService.createCapitalInjection(request);
    }
}

