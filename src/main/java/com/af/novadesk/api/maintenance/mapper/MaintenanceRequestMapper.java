package com.af.novadesk.api.maintenance.mapper;

import com.af.novadesk.api.maintenance.dto.MaintenanceRequestDto;
import com.af.novadesk.api.maintenance.entity.MaintenanceRequest;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.List;

@Mapper(
    componentModel = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface MaintenanceRequestMapper {

    @Mapping(target = "assetId", source = "asset.id")
    @Mapping(target = "assetSerialNumber", source = "asset.serialNumber")
    @Mapping(target = "assignedTechnicianLabel",
            expression = "java(entity.getAssignedTechnicianId().toString().substring(0, 8))")
    MaintenanceRequestDto toDto(MaintenanceRequest entity);

    List<MaintenanceRequestDto> toDtoList(List<MaintenanceRequest> entities);
}
