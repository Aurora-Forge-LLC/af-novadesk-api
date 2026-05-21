package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.finance.entity.LegalEntity;
import org.mapstruct.*;

import java.util.List;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface EntityUserAccessMapper {

    @Mapping(target = "authUserId",    source = "shadowUser.authUserId")
    @Mapping(target = "email",         source = "shadowUser.email")
    @Mapping(target = "displayName",   source = "shadowUser.displayName")
    @Mapping(target = "legalEntityId", source = "legalEntity.id")
    @Mapping(target = "entityName",    source = "legalEntity.entityName")
    EntityUserAccessDto toDto(EntityUserAccess entity);

    List<EntityUserAccessDto> toAccessDtoList(List<EntityUserAccess> entities);

    @Mapping(target = "legalEntityId", source = "id")
    @Mapping(target = "entityName",    source = "entityName")
    @Mapping(target = "entityCode",    source = "entityCode")
    @Mapping(target = "baseCurrency",  source = "baseCurrency")
    EntityContextDto toContextDto(LegalEntity entity);

    LegalEntitySummaryDto toSummaryDto(LegalEntity entity);

    List<LegalEntitySummaryDto> toSummaryDtoList(List<LegalEntity> entities);
}
