package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.AcknowledgmentStatus;
import com.af.novadesk.api.asset.constants.AssignmentPurpose;
import com.af.novadesk.api.asset.constants.AssignmentStatus;
import com.af.novadesk.api.asset.constants.ConditionGrade;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class AssetAssignmentDto {
    private UUID id;
    private UUID assetId;
    private String assetType;
    private String serialNumber;
    private UUID employeeId;
    private UUID assignedBy;
    private LocalDate assignmentDate;
    private LocalDate expectedReturnDate;
    private AssignmentPurpose purpose;
    private ConditionGrade conditionAtAssignment;
    private boolean requiresAcknowledgment;
    private AcknowledgmentStatus acknowledgmentStatus;
    private LocalDateTime acknowledgmentAt;
    private AssignmentStatus assignmentStatus;
    private String notes;
    private LocalDateTime createdAt;
}
