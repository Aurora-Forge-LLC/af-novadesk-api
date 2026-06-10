package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LeavePolicyDto {

    private UUID id;
    private UUID legalEntityId;
    private String name;
    private LeavePaymentType paymentType;
    private Integer allowedDays;
    private Boolean isUnlimited;
    private Boolean isEarned;
    private Integer borrowMultiple;
    private UUID createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
