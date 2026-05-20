package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.FiscalYearSettingDto;
import com.af.novadesk.api.finance.entity.FiscalYearSetting;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface FiscalYearSettingMapper {

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "legalEntity", ignore = true)
    @Mapping(target = "status",      ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    FiscalYearSetting toEntity(FiscalYearSettingDto dto);

    FiscalYearSettingDto toDto(FiscalYearSetting entity);

    @Mapping(target = "id",          ignore = true)
    @Mapping(target = "legalEntity", ignore = true)
    @Mapping(target = "status",      ignore = true)
    @Mapping(target = "createdAt",   ignore = true)
    @Mapping(target = "updatedAt",   ignore = true)
    void updateEntity(FiscalYearSettingDto dto, @MappingTarget FiscalYearSetting target);
}
