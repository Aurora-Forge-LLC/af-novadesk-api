package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.EntityContextDto;
import com.af.novadesk.api.finance.dto.EntityUserAccessDto;
import com.af.novadesk.api.finance.dto.LegalEntitySummaryDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for user ↔ entity access grants and entity context switching
 * (LLR-FIN-01.3).
 */
public interface EntityUserAccessService {

    EntityUserAccessDto grantAccess(UUID entityId, EntityUserAccessDto request);

    void revokeAccess(UUID entityId, UUID accessId);

    EntityUserAccessDto updateRole(UUID entityId, UUID accessId, EntityUserAccessDto request);

    EntityContextDto selectEntityContext(EntityContextDto request);

    List<EntityUserAccessDto> listAccessForEntity(UUID entityId);

    List<LegalEntitySummaryDto> listAccessibleEntities();
}
