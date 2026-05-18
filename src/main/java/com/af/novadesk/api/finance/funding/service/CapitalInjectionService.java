package com.af.novadesk.api.finance.funding.service;

import com.af.novadesk.api.finance.funding.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.funding.dto.CapitalInjectionResponse;

public interface CapitalInjectionService {

    CapitalInjectionResponse createCapitalInjection(CapitalInjectionRequest request);
}

