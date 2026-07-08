package com.af.novadesk.api.asset.dto;

import com.af.novadesk.api.asset.constants.AssetDeductionStatus;
import com.af.novadesk.api.asset.constants.WriteOffReason;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class AssetPayrollDeductionDto {

    private UUID id;
    private UUID writeOffId;
    private UUID assetId;
    private String assetLabel;
    private UUID employeeId;
    private String employeeName;
    private BigDecimal amount;
    private String currencyCode;
    private LocalDate deductionDate;
    private WriteOffReason writeOffReason;
    private AssetDeductionStatus deductionStatus;
    private UUID payrollBatchId;
    private UUID payslipId;
    private LocalDateTime createdAt;
}
