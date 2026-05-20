package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.ChartOfAccountDto;
import com.af.novadesk.api.finance.entity.ChartOfAccount;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ChartOfAccountMapper {

    @Mapping(target = "parentAccountId", source = "parentAccount.id")
    ChartOfAccountDto toDto(ChartOfAccount entity);

    List<ChartOfAccountDto> toChartOfAccountDtoList(List<ChartOfAccount> entities);
}