package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.EntityBankAccountDto;
import com.af.novadesk.api.finance.entity.EntityBankAccount;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface EntityBankAccountMapper {

    EntityBankAccountDto toDto(EntityBankAccount entity);

    List<EntityBankAccountDto> toBankAccountDtoList(List<EntityBankAccount> entities);
}
