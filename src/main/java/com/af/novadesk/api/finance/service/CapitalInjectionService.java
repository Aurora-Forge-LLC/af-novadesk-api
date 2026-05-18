package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.CapitalInjectionRequest;
import com.af.novadesk.api.finance.dto.CapitalInjectionResponse;

public interface CapitalInjectionService {

    CapitalInjectionResponse createCapitalInjection(CapitalInjectionRequest request);
}

