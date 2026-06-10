package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.StatementStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO representing a bank statement's metadata for list/detail views.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankStatementDto {

    private UUID id;
    private UUID legalEntityId;
    private UUID bankAccountId;
    private UUID uploadedByUserId;
    private String uploadedByDisplayName;
    private String originalFilename;
    private String fileType;
    private Integer fileSizeBytes;
    private Boolean isEncrypted;
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private Integer transactionCount;
    private String notes;
    private StatementStatus statementStatus;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
