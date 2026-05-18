package com.af.novadesk.api.finance.funding.service;

import com.af.novadesk.api.finance.funding.dto.LegalEntitySummaryResponse;

import java.util.List;
import java.util.UUID;

public interface LegalEntityService {

    List<LegalEntitySummaryResponse> list();

    LegalEntitySummaryResponse getById(UUID id);
}

