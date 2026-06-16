package com.af.novadesk.api.payroll.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MoveEmployeeRequest {

    @NotNull(message = "Source entity ID is required")
    private UUID fromEntityId;

    @NotNull(message = "Target entity ID is required")
    private UUID toEntityId;

    /** Whether to make the target entity the primary payroll-processing entity. */
    @Builder.Default
    private boolean makePrimary = false;
}
