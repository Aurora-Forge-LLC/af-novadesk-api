package com.af.novadesk.api.common.dto;

import lombok.Data;
import java.util.UUID;

/**
 * Lightweight employee record — used for dropdowns and cross-module lookups.
 * Full payroll data lives in the payroll module.
 */
@Data
public class EmployeeDto {
    private UUID   id;
    private UUID   organizationId;
    private String employeeCode;
    private String displayName;
    private String email;
    private String employeeStatus;
}
