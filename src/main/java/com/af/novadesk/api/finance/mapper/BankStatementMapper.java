package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.entity.BankStatement;
import org.springframework.stereotype.Component;

/**
 * Manual mapper for {@link BankStatement} → {@link BankStatementDto}.
 *
 * <p>Avoids MapStruct annotation-processor dependency (incompatible with
 * certain JDK 21+ builds).  Kept as a {@link Component} so it participates
 * in dependency injection just like a MapStruct-generated bean.</p>
 */
@Component
public class BankStatementMapper {

    /**
     * Converts a {@link BankStatement} entity to its DTO representation.
     *
     * @param entity the persisted bank-statement entity (must not be {@code null})
     * @return fully populated DTO
     */
    public BankStatementDto toDto(BankStatement entity) {
        BankStatementDto dto = new BankStatementDto();

        dto.setId(entity.getId());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());

        // Legal entity
        if (entity.getLegalEntity() != null) {
            dto.setLegalEntityId(entity.getLegalEntity().getId());
            dto.setLegalEntityName(entity.getLegalEntity().getEntityName());
        }

        // Bank account
        if (entity.getBankAccount() != null) {
            dto.setBankAccountId(entity.getBankAccount().getId());
            dto.setBankAccountLabel(entity.getBankAccount().getAccountLabel());
        }

        // File metadata
        dto.setOriginalFilename(entity.getOriginalFilename());
        dto.setFileType(entity.getFileType());
        dto.setFileSizeBytes(entity.getFileSizeBytes());
        dto.setEncrypted(entity.isEncrypted());

        // Statement period
        dto.setPeriodStart(entity.getPeriodStart());
        dto.setPeriodEnd(entity.getPeriodEnd());

        // Notes
        dto.setNotes(entity.getNotes());

        // Status & counts
        dto.setStatementStatus(entity.getStatementStatus());
        dto.setReconciliationStatus(entity.getReconciliationStatus());
        dto.setTransactionCount(entity.getTransactionCount());

        // Uploaded-by user
        if (entity.getUploadedBy() != null) {
            dto.setUploadedByUserId(entity.getUploadedBy().getId());
            dto.setUploadedByName(entity.getUploadedBy().getDisplayName());
        }

        return dto;
    }
}
