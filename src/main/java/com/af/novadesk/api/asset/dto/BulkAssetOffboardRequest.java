package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.ConditionGrade;
import lombok.Data;

/**
 * Request to bulk-offboard all ACTIVE asset assignments for an employee.
 */
@Data
public class BulkAssetOffboardRequest {

    /** Default condition grade for returned assets. Defaults to GOOD if not specified. */
    private ConditionGrade conditionAtReturn;

    /** Optional notes for the offboarding. */
    private String notes;
}
