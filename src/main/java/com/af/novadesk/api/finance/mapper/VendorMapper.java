package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.finance.dto.VendorDto;
import com.af.novadesk.api.finance.entity.Vendor;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

/**
 * MapStruct mapper for {@link Vendor} → {@link VendorDto}.
 *
 * <p>{@code defaultAccountId} is sourced from the optional {@code defaultAccount}
 * association. MapStruct safely maps {@code null} to {@code null} when no default
 * account is set (NullValuePropertyMappingStrategy.IGNORE).</p>
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface VendorMapper {

    @Mapping(target = "defaultAccountId", source = "defaultAccount.id")
    VendorDto toDto(Vendor entity);
}
