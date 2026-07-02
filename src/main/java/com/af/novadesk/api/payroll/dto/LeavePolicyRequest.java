package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LeavePolicyRequest {

    @NotNull(message = "Legal entity ID is required")
    private UUID legalEntityId;

    @NotBlank(message = "Policy name is required")
    @Size(max = 100, message = "Policy name must be at most 100 characters")
    private String name;

    @NotNull(message = "Payment type is required")
    private LeavePaymentType paymentType;

    @NotNull(message = "Allowed days is required")
    @Min(value = 0, message = "Allowed days must be 0 or greater")
    private Integer allowedDays;

    private Boolean isUnlimited = false;

    private Boolean isEarned = false;

    /** Required when isEarned is true, ignored otherwise. */
    private Integer borrowMultiple;
}
