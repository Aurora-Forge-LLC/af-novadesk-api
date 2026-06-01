package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.ExpenseTransactionDto;
import com.af.novadesk.api.finance.entity.ExpenseTransaction;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for {@link ExpenseTransaction} → {@link ExpenseTransactionDto}.
 *
 * <p>Delegates attachment list mapping to {@link ExpenseAttachmentMapper} via
 * {@code uses}. MapStruct will automatically apply that mapper when it encounters
 * a {@code List<ExpenseAttachment>} → {@code List<ExpenseAttachmentDto>} conversion.</p>
 *
 * <p>The {@code amount} field is scaled to 2 decimal places for display.
 * Internal storage uses 4dp (DECIMAL(19,4)) for exchange-rate precision;
 * the response always shows standard 2dp monetary values (e.g. "750.00").</p>
 */
@Mapper(
        componentModel = "spring",
        uses = { ExpenseAttachmentMapper.class },
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ExpenseTransactionMapper {

    @Mapping(target = "legalEntityId",        source = "legalEntity.id")
    @Mapping(target = "vendorId",             source = "vendor.id")
    @Mapping(target = "sourceAccountId",      source = "sourceAccount.id")
    @Mapping(target = "chartOfAccountId",     source = "chartOfAccount.id")
    @Mapping(target = "createdByUserId",      source = "createdBy.id")
    @Mapping(target = "amount",               expression = "java(entity.getAmount().setScale(2, java.math.RoundingMode.HALF_UP))")
    ExpenseTransactionDto toDto(ExpenseTransaction entity);
}
