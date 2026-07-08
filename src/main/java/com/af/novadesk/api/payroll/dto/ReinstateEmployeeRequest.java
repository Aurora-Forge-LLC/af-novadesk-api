package com.af.novadesk.api.payroll.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data
public class ReinstateEmployeeRequest {

    @NotNull(message = "Legal entity ID is required")
    private UUID legalEntityId;

    @NotNull(message = "Hire date is required")
    @PastOrPresent(message = "Hire date must not be in the future")
    private LocalDate hireDate;

    private String department;
    private String designation;

    @Positive(message = "Base salary must be positive")
    private BigDecimal baseSalary;

    private String salaryCurrency;
    private Boolean isManager;
}
