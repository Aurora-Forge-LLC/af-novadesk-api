package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import org.mapstruct.*;

import java.util.List;
import java.util.UUID;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ChartOfAccountMapper {

    @Mapping(target = "parentAccountId", source = "parentAccount", qualifiedByName = "parentAccountId")
    ChartOfAccountDto toDto(ChartOfAccount entity);

    List<ChartOfAccountDto> toChartOfAccountDtoList(List<ChartOfAccount> entities);

    @Named("parentAccountId")
    default UUID parentAccountId(ChartOfAccount parentAccount) {
        return parentAccount != null ? parentAccount.getId() : null;
    }
}