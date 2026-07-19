package com.af.novadesk.api.asset.reservation.mapper;

import com.af.novadesk.api.asset.reservation.dto.ReservationDto;
import com.af.novadesk.api.asset.reservation.entity.AssetReservation;
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
public interface ReservationMapper {

    @Mapping(target = "assetId",      source = "asset.id")
    @Mapping(target = "assetType",    source = "asset.assetType")
    @Mapping(target = "serialNumber", source = "asset.serialNumber")
    @Mapping(target = "status",       source = "reservationStatus")
    ReservationDto toDto(AssetReservation entity);

    List<ReservationDto> toDtoList(List<AssetReservation> entities);
}
