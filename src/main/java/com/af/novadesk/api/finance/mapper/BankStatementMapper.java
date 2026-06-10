package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.BankStatementDto;
import com.af.novadesk.api.finance.dto.BankTransactionDto;
import com.af.novadesk.api.finance.entity.BankStatement;
import com.af.novadesk.api.finance.entity.BankTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for bank statement and transaction entities → DTOs.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface BankStatementMapper {

    @Mapping(target = "legalEntityId",        source = "legalEntity.id")
    @Mapping(target = "bankAccountId",         source = "bankAccount.id")
    @Mapping(target = "uploadedByUserId",      source = "uploadedBy.id")
    @Mapping(target = "uploadedByDisplayName", source = "uploadedBy.displayName")
    BankStatementDto toDto(BankStatement entity);

    @Mapping(target = "legalEntityId",  source = "legalEntity.id")
    @Mapping(target = "bankAccountId",   source = "bankAccount.id")
    @Mapping(target = "statementId",     source = "statement.id")
    BankTransactionDto toTxnDto(BankTransaction entity);
}
