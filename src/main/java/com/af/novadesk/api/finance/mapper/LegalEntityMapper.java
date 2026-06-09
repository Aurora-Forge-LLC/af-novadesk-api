package com.af.novadesk.api.finance.mapper;

import com.af.novadesk.api.common.entity.FiscalYearSetting;
import com.af.novadesk.api.common.entity.LegalEntity;
import com.af.novadesk.api.identity.dto.ShadowUserDto;
import com.af.novadesk.api.finance.entity.*;
import com.af.novadesk.api.finance.dto.*;
import com.af.novadesk.api.finance.entity.EntityUserAccess;
import com.af.novadesk.api.identity.entity.ShadowUser;
import org.mapstruct.*;

import java.util.List;

/**
 * MapStruct mapper for the Finance module.
 *
 * <p>Works with unified DTOs — the same DTO type is used inbound and outbound.
 * Entity → DTO mappings populate server-assigned fields.
 * DTO → Entity mappings ignore server-assigned fields (they are null inbound
 * and set by the service after mapping).</p>
 */
@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface LegalEntityMapper {

    // =========================================================================
    // LegalEntity
    // =========================================================================

    /**
     * DTO → Entity (inbound / create).
     * Ignores all server-assigned fields — the service sets them after this call.
     */
    @Mapping(target = "id",                ignore = true)
    @Mapping(target = "baseCurrency",      ignore = true)
    @Mapping(target = "approvalStatus",    ignore = true)
    @Mapping(target = "status",            ignore = true)
    @Mapping(target = "fiscalYearSetting", ignore = true)
    @Mapping(target = "chartOfAccounts",   ignore = true)
    @Mapping(target = "bankAccounts",      ignore = true)
    @Mapping(target = "userAccesses",      ignore = true)
    @Mapping(target = "createdAt",         ignore = true)
    @Mapping(target = "updatedAt",         ignore = true)
    LegalEntity toEntity(LegalEntityDto dto);

    /**
     * Entity → DTO (outbound).
     * All server-assigned fields are populated from the entity.
     */
    @Mapping(target = "fiscalYearSetting", source = "fiscalYearSetting")
    @Mapping(target = "chartOfAccounts",   source = "chartOfAccounts")
    @Mapping(target = "bankAccounts",      source = "bankAccounts")
    LegalEntityDto toDto(LegalEntity entity);

    /** Lightweight summary for list/selector endpoints. */
    LegalEntitySummaryDto toSummaryDto(LegalEntity entity);

    List<LegalEntitySummaryDto> toSummaryDtoList(List<LegalEntity> entities);

    // =========================================================================
    // FiscalYearSetting
    // =========================================================================

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

    // =========================================================================
    // ChartOfAccount
    // =========================================================================

    @Mapping(target = "parentAccountId", source = "parentAccount.id")
    ChartOfAccountDto toDto(ChartOfAccount entity);

    List<ChartOfAccountDto> toChartOfAccountDtoList(List<ChartOfAccount> entities);

    // =========================================================================
    // EntityBankAccount
    // =========================================================================

    EntityBankAccountDto toDto(EntityBankAccount entity);

    List<EntityBankAccountDto> toBankAccountDtoList(List<EntityBankAccount> entities);

    // =========================================================================
    // EntityUserAccess
    // =========================================================================

    /**
     * Entity → DTO (outbound).
     * Flattens shadow-user and legal-entity references into the unified DTO.
     */
    @Mapping(target = "authUserId",    source = "shadowUser.authUserId")
    @Mapping(target = "email",         source = "shadowUser.email")
    @Mapping(target = "displayName",   source = "shadowUser.displayName")
    @Mapping(target = "legalEntityId", source = "legalEntity.id")
    @Mapping(target = "entityName",    source = "legalEntity.entityName")
    EntityUserAccessDto toDto(EntityUserAccess entity);

    List<EntityUserAccessDto> toAccessDtoList(List<EntityUserAccess> entities);

    // =========================================================================
    // ShadowUser
    // =========================================================================

    ShadowUserDto toDto(ShadowUser entity);

    // =========================================================================
    // EntityContextDto
    // =========================================================================

    @Mapping(target = "legalEntityId", source = "entity.id")
    @Mapping(target = "entityName",    source = "entity.entityName")
    @Mapping(target = "entityCode",    source = "entity.entityCode")
    @Mapping(target = "baseCurrency",  source = "entity.baseCurrency")
    @Mapping(target = "selectedAt",    expression = "java(java.time.LocalDateTime.now())")
    EntityContextDto toContextDto(LegalEntity entity);
}