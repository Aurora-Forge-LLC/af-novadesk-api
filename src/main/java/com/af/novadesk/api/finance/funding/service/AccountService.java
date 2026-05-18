package com.af.novadesk.api.finance.funding.service;

import com.af.novadesk.api.finance.funding.dto.AccountSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    List<AccountSummaryResponse> list();

    AccountSummaryResponse getById(UUID id);
}

