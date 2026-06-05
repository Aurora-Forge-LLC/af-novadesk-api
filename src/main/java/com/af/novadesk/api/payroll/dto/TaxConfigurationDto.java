package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Combined DTO for TaxConfiguration — per-entity, per-jurisdiction tax rules (PAY-04.6).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaxConfigurationDto {

    private UUID id;

    @NotNull(message = "Legal entity ID is required")
    private UUID legalEntityId;
    private String legalEntityName;

    @NotNull(message = "Jurisdiction is required")
    private Jurisdiction jurisdiction;

    // SSF rates (Nepal)
    private BigDecimal ssfEmployeeRate;          // 0.11 for Nepal (11%)
    private BigDecimal ssfEmployerRate;          // 0.20 for Nepal (20%)
    private BigDecimal ssfMaxCapAmount;          // NPR 50,000 max gross for SSF

    // PF rates (India)
    private BigDecimal pfEmployeeRate;           // 0.12 for India (12%)
    private BigDecimal pfEmployerRate;           // 0.12 for India (12%)
    private BigDecimal pfMaxCapAmount;           // INR 15,000 for India PF cap

    // Professional Tax (India)
    private BigDecimal professionalTaxAmount;    // Fixed amount, e.g. INR 200/month
    private String professionalTaxState;         // State, e.g. "Karnataka"

    // Metadata
    private LocalDate effectiveFrom;
    private LocalDate effectiveTo;               // Null = currently active
    private Boolean isActive;

    private UUID lastModifiedById;
    private String lastModifiedByName;

    // Children
    private List<TaxSlabDto> taxSlabs;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
