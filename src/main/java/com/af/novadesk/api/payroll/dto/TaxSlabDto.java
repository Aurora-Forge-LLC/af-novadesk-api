package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for TaxSlab — individual progressive tax slab (PAY-04.3–04.4).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TaxSlabDto {

    private UUID id;
    private UUID taxConfigurationId;

    @NotNull(message = "Slab order is required")
    @Positive(message = "Slab order must be positive")
    private Integer slabOrder;

    @NotNull(message = "Income from is required")
    private BigDecimal incomeFrom;               // Lower bound (inclusive)

    private BigDecimal incomeTo;                 // Upper bound (null = unlimited)

    @NotNull(message = "Tax rate is required")
    private BigDecimal taxRate;                  // 0.01 for 1%, 0.10 for 10%

    private Boolean isAnnual;                    // true = annual slab, divide by 12 for monthly

    private String description;                  // e.g. "NPR 0 - 500,000: 1%"

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
