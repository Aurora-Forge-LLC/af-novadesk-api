package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.VendorDto;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.Vendor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.util.UUID;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface VendorMapper {

    @Mapping(target = "defaultAccountId", source = "defaultAccount", qualifiedByName = "defaultAccountId")
    VendorDto toDto(Vendor entity);

    @Named("defaultAccountId")
    default UUID defaultAccountId(Account defaultAccount) {
        return defaultAccount != null ? defaultAccount.getId() : null;
    }
}
