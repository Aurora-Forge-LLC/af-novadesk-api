package com.af.novadesk.api.organization.mapper;

import com.af.novadesk.api.organization.dto.OrganizationBankInfoRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoResponse;
import com.af.novadesk.api.organization.entity.OrganizationBankInfo;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for {@link OrganizationBankInfo} entity {@literal <->} DTO conversions.
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface OrganizationBankInfoMapper {

    /**
     * Maps an entity to a response DTO.
     */
    OrganizationBankInfoResponse toResponse(OrganizationBankInfo entity);

    /**
     * Maps a list of entities to a list of response DTOs.
     */
    List<OrganizationBankInfoResponse> toResponseList(List<OrganizationBankInfo> entities);

    /**
     * Maps a request DTO to an entity (used for create).
     * Fields like {@code id}, {@code orgId}, {@code userId}, {@code createdAt},
     * {@code updatedAt}, and {@code status} are ignored as they are set server-side.
     */
    OrganizationBankInfo toEntity(OrganizationBankInfoRequest request);

    /**
     * Merges a request DTO into an existing entity (used for update).
     * Only non-null properties from the request are applied.
     *
     * @param entity  the existing entity to update
     * @param request the request DTO with new values
     */
    void updateEntity(@MappingTarget OrganizationBankInfo entity, OrganizationBankInfoRequest request);
}
