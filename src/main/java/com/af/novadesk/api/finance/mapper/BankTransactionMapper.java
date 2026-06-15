package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.BankTransactionDto;
import com.af.novadesk.api.finance.entity.BankTransaction;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Manual mapper for {@link BankTransaction} → {@link BankTransactionDto}.
 */
@Component
public class BankTransactionMapper {

    /**
     * Convert a {@link BankTransaction} entity to its DTO representation.
     */
    public BankTransactionDto toDto(BankTransaction entity) {
        return BankTransactionDto.builder()
                .id(entity.getId())
                .transactionDate(entity.getTransactionDate())
                .description(entity.getDescription())
                .debit(entity.getAmount() != null && entity.getAmount().signum() < 0
                        ? entity.getAmount().abs() : null)
                .credit(entity.getAmount() != null && entity.getAmount().signum() > 0
                        ? entity.getAmount() : null)
                .balance(entity.getBalance())
                .signedAmount(entity.getAmount())
                .reconciliationStatus(entity.getReconciliationStatus() != null
                        ? entity.getReconciliationStatus().name() : null)
                .bankAccountLabel(entity.getStatement() != null
                        && entity.getStatement().getBankAccount() != null
                        ? entity.getStatement().getBankAccount().getAccountLabel() : null)
                .build();
    }

    /**
     * Convert a list of entities to DTOs.
     */
    public List<BankTransactionDto> toDtoList(List<BankTransaction> entities) {
        return entities.stream()
                .map(this::toDto)
                .toList();
    }
}
