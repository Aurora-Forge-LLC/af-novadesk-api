package com.af.novadesk.api.asset.mapper;

import com.af.novadesk.api.asset.dto.*;
import com.af.novadesk.api.asset.entity.*;
import org.mapstruct.*;

import java.util.List;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AssetMapper {

    @Mapping(target = "legalEntityId", source = "legalEntity.id")
    AssetDto toDto(Asset entity);

    List<AssetDto> toDtoList(List<Asset> entities);

    @Mapping(target = "assetId",     source = "asset.id")
    @Mapping(target = "assetType",   source = "asset.assetType")
    @Mapping(target = "serialNumber", source = "asset.serialNumber")
    AssetAssignmentDto toAssignmentDto(AssetAssignment entity);

    List<AssetAssignmentDto> toAssignmentDtoList(List<AssetAssignment> entities);

    @Mapping(target = "assetId", source = "asset.id")
    CustodyTransferDto toCustodyTransferDto(AssetCustodyTransfer entity);

    List<CustodyTransferDto> toCustodyTransferDtoList(List<AssetCustodyTransfer> entities);

    @Mapping(target = "assetId", source = "asset.id")
    DepreciationScheduleDto toDepreciationDto(DepreciationSchedule entity);

    List<DepreciationScheduleDto> toDepreciationDtoList(List<DepreciationSchedule> entities);
}
