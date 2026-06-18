package com.af.novadesk.api.payroll.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
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
@JsonIgnoreProperties(ignoreUnknown = true)
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
    @PastOrPresent(message = "Hire date must not be in the future")
    private LocalDate hireDate;

    private LocalDate terminationDate;

    // Manager
    private UUID managerId;
    private String managerName;

    /**
     * Request field: set to true on employee onboarding (-assign manager role" toggle).
     * Response field: indicates whether this employee has a MANAGER entity role.
     */
    private Boolean isManager;

    /**
     * Response-only field: generated UUID when isManager is true during onboarding.
     * Null for non-manager employees.
     */
    private UUID managerUuid;

    // Compensation
    @NotNull(message = "Base salary is required")
    @Positive(message = "Base salary must be positive")
    private BigDecimal baseSalary;

    /** May be omitted; auto-derived from the entity's base currency if not provided. */
    @JsonProperty("currencyCode")
    private String salaryCurrency;

    // Bank details
    private String bankAccountNumber;
    private String bankName;

    /**
     * Accepts both {@code bankIfscCode} (primary) and {@code bankRoutingNumber} (alias)
     * for backward compatibility with frontend clients.
     */
    @JsonAlias("bankRoutingNumber")
    private String bankIfscCode;

    // Audit (response)
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String status;
}
