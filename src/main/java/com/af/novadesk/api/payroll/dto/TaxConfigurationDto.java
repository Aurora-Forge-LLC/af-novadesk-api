package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.Jurisdiction;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
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

    private String taxName;                       // Human-readable label, e.g. "Income Tax 2026"

    private String taxType;                       // Discriminator for multiple configs per entity+jurisdiction

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
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

    private String calculationMethod;           // PROGRESSIVE or FLAT_ON_CAP

    // Unified flat-rate fields (replace ssf_*/pf_*)
    private BigDecimal flatEmployeeRate;        // e.g. 6.2 = 6.2%
    private BigDecimal flatEmployerRate;        // e.g. 6.2 = 6.2% employer match
    private BigDecimal flatCapAmount;           // Monthly tax-amount cap (null = unlimited)

    private UUID lastModifiedById;
    private String lastModifiedByName;

    // Children
    private List<TaxSlabDto> taxSlabs;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
