package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.AssignmentPurpose;
import com.af.novadesk.api.asset.constants.ConditionGrade;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class AssetAssignmentRequest {

    @NotNull(message = "Employee is required")
    private UUID employeeId;

    @NotNull(message = "Assignment date is required")
    private LocalDate assignmentDate;

    private LocalDate expectedReturnDate;

    private AssignmentPurpose purpose = AssignmentPurpose.PRIMARY_WORK;

    private ConditionGrade conditionAtAssignment = ConditionGrade.GOOD;

    @Size(max = 500)
    private String notes;
}
