package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.ConditionGrade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetReturnRequest {

    @NotNull(message = "Return date is required")
    private LocalDate returnDate;

    @NotNull(message = "Received-by employee is required")
    private UUID receivedByEmployeeId;

    @NotNull(message = "Condition at return is required")
    private ConditionGrade conditionAtReturn;

    private boolean repairRequired = false;

    @Size(max = 1000)
    private String notes;
}
