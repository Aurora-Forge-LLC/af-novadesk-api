package com.af.novadesk.api.finance.service.impl;
import com.af.novadesk.api.finance.dto.AccountSummaryResponse;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.service.AccountService;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.UUID;
@Service
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    public AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }
    @Override
    public List<AccountSummaryResponse> list() {
        return accountRepository.findAll().stream().map(this::toSummary).toList();
    }
    @Override
    public AccountSummaryResponse getById(UUID id) {
        Account account = accountRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Account not found with id: " + id));
        return toSummary(account);
    }
    private AccountSummaryResponse toSummary(Account account) {
        return new AccountSummaryResponse(
                account.getId(),
                account.getLegalEntity().getId(),
                account.getAccountCode(),
                account.getAccountName(),
                account.getAccountRole(),
                account.getAccountType(),
                account.getCurrencyCode(),
                account.getStatus(),
                account.getCreatedAt()
        );
    }
}
