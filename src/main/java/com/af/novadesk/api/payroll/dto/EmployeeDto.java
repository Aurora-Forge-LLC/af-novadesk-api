package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Combined DTO for Employee — serves as request, response, and summary.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EmployeeDto {

    // Identity (response)
    private UUID id;
    private UUID organizationId;

    // ShadowUser link (optional — set automatically in new reversed flow)
    private UUID shadowUserId;
    private UUID authUserId;

    // Entity
    @NotNull(message = "Legal entity ID is required")
    private UUID legalEntityId;
    private String legalEntityName;

    // Employee code
    @NotBlank(message = "Employee code is required")
    private String employeeCode;

    // Personal details (required for new reversed flow)
    @NotBlank(message = "First name is required")
    private String firstName;

    @NotBlank(message = "Last name is required")
    private String lastName;

    @NotBlank(message = "Email is required")
    private String email;
    private String displayName;       // derived: firstName + lastName

    // Employment
    private String department;
    private String designation;

    @NotNull(message = "Hire date is required")
    private LocalDate hireDate;

    private LocalDate terminationDate;

    // Manager
    private UUID managerId;
    private String managerName;

    // Compensation
    @NotNull(message = "Base salary is required")
    @Positive(message = "Base salary must be positive")
    private BigDecimal baseSalary;

    @NotBlank(message = "Salary currency is required")
    @JsonProperty("currencyCode")
    private String salaryCurrency;

    // Bank details
    private String bankAccountNumber;
    private String bankName;
    private String bankIfscCode;

    // Audit (response)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
}
