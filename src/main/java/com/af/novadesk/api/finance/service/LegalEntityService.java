package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.ApprovalStatus;
import com.af.novadesk.api.finance.constants.CountryCode;
import com.af.novadesk.api.finance.dto.ApproveEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityDto;
import com.af.novadesk.api.finance.dto.LegalEntityPageDto;
import com.af.novadesk.api.finance.dto.RejectEntityDto;
import com.af.novadesk.api.finance.dto.UpdateEntityStatusRequest;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

/**
 * Core service contract for Multi-Entity Management (LLR-FIN-01).
 *
 * <p>Orchestrates entity creation, approval/rejection lifecycle, status management,
 * and domain event publishing.</p>
 */
public interface LegalEntityService {

    LegalEntityDto createLegalEntity(LegalEntityDto request);

    LegalEntityDto approveEntity(UUID entityId, ApproveEntityDto request);

    LegalEntityDto rejectEntity(UUID entityId, RejectEntityDto request);

    LegalEntityDto updateStatus(UUID entityId, UpdateEntityStatusRequest request);

    LegalEntityDto getById(UUID entityId);

    /** @deprecated Use {@link #list} with filter params instead. */
    @Deprecated
    LegalEntityPageDto listAll(Pageable pageable);

    LegalEntityPageDto list(String q, Status status, ApprovalStatus approvalStatus,
                            CountryCode country, String baseCurrency, Pageable pageable);
}
