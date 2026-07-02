package com.af.novadesk.api.payroll.api;

import com.af.novadesk.api.common.response.ApiResponse;
import com.af.novadesk.api.payroll.dto.TaxConfigurationDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Payroll - Tax Configuration", description = "Tax rule configuration — LLR-PAY-04.6")
@RequestMapping("/api/v1/payroll/tax-configurations")
@SecurityRequirement(name = "bearerAuth")
public interface TaxConfigurationApi {

    @Operation(summary = "List tax configs by entity")
    @GetMapping
    @PreAuthorize("hasAuthority('payroll:read')")
    ResponseEntity<ApiResponse<List<TaxConfigurationDto>>> listByEntity(
            @Parameter(description = "Optional legal entity ID to filter tax configs by entity. " +
                    "If omitted, returns tax configs for all entities.")
            @RequestParam(required = false) UUID legalEntityId);

    @Operation(summary = "Get tax config detail")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll:read')")
    ResponseEntity<ApiResponse<TaxConfigurationDto>> getConfig(@PathVariable UUID id);

    @Operation(summary = "Create tax configuration. taxType defaults to GENERAL. Nepal entities can only have one config.")
    @PostMapping
    @PreAuthorize("hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<TaxConfigurationDto>> createConfig(@Valid @RequestBody TaxConfigurationDto request);

    @Operation(summary = "Update tax configuration")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll:manage')")
    ResponseEntity<ApiResponse<TaxConfigurationDto>> updateConfig(
            @PathVariable UUID id, @Valid @RequestBody TaxConfigurationDto request);

    @Operation(summary = "Soft-delete tax configuration (sets status to DELETED)")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('payroll:delete')")
    ResponseEntity<ApiResponse<Void>> deleteConfig(@PathVariable UUID id);

    @Operation(summary = "Hard-delete tax configuration (removes from database)")
    @DeleteMapping("/{id}/hard")
    @PreAuthorize("hasAuthority('payroll:delete')")
    ResponseEntity<ApiResponse<Void>> hardDeleteConfig(@PathVariable UUID id);
}
