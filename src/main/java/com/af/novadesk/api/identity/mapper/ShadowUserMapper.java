package com.af.novadesk.api.identity.mapper;

import com.af.novadesk.api.identity.dto.ShadowUserDto;
import com.af.novadesk.api.identity.entity.ShadowUser;
import org.mapstruct.*;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface ShadowUserMapper {

    ShadowUserDto toDto(ShadowUser entity);
}