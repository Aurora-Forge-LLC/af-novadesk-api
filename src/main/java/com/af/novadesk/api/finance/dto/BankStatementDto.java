package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.ReconciliationStatus;
import com.af.novadesk.api.finance.constants.StatementStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * DTO representing a bank statement record for list/detail views (LLR-BNK-01).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Uploaded bank statement metadata with parsed transactions")
public class BankStatementDto {

    @Schema(description = "Statement UUID")
    private UUID id;

    @Schema(description = "Legal entity UUID")
    private UUID legalEntityId;

    @Schema(description = "Legal entity name")
    private String legalEntityName;

    @Schema(description = "Bank account UUID")
    private UUID bankAccountId;

    @Schema(description = "Bank account label")
    private String bankAccountLabel;

    @Schema(description = "Uploaded by shadow user UUID")
    private UUID uploadedByUserId;

    @Schema(description = "Uploader display name")
    private String uploadedByName;

    @Schema(description = "Original filename")
    private String originalFilename;

    @Schema(description = "File type extension (CSV, XLSX, XLS)")
    private String fileType;

    @Schema(description = "File size in bytes")
    private Integer fileSizeBytes;

    @Schema(description = "Whether the file is encrypted")
    private boolean isEncrypted;

    @Schema(description = "Statement period start date")
    private LocalDate periodStart;

    @Schema(description = "Statement period end date")
    private LocalDate periodEnd;

    @Schema(description = "Number of transactions extracted")
    private Integer transactionCount;

    @Schema(description = "User notes")
    private String notes;

    @Schema(description = "Processing status: UPLOADED, PARSED, SUPERSEDED, FAILED")
    private StatementStatus statementStatus;

    @Schema(description = "Reconciliation state: UNMATCHED, SUGGESTED, MATCHED, IGNORED")
    private ReconciliationStatus reconciliationStatus;

    @Schema(description = "When the statement was uploaded")
    private LocalDateTime createdAt;

    @Schema(description = "Last updated timestamp")
    private LocalDateTime updatedAt;

    @Schema(description = "Parsed transactions from the statement file. Populated on upload and detail views.")
    @Builder.Default
    private List<BankTransactionDto> transactions = new ArrayList<>();
}
