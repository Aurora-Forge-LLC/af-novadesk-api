package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.AccountSummaryResponse;

import java.util.List;
import java.util.UUID;

public interface AccountService {

    List<AccountSummaryResponse> list();

    AccountSummaryResponse getById(UUID id);
}

