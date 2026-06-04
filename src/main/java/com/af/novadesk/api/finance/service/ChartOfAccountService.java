package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.constants.AccountType;
import com.af.novadesk.api.finance.dto.ChartOfAccountDto;

import java.util.List;
import java.util.UUID;

/**
 * Service contract for querying the Chart of Accounts.
 *
 * <p>Provides entity-scoped lookups used by the expense form to populate
 * the account category dropdown (replacing the old destination account selector).</p>
 */
public interface ChartOfAccountService {

    /**
     * Returns all Chart of Accounts entries for the given legal entity,
     * ordered by account code ascending.
     *
     * @param legalEntityId the entity whose CoA entries to return
     * @return list of CoA entries, empty if none found
     */
    List<ChartOfAccountDto> listByEntity(UUID legalEntityId);

    /**
     * Returns Chart of Accounts entries for the given legal entity,
     * filtered by account type.
     *
     * <p>Use {@code accountType = EXPENSE} to populate the expense category
     * dropdown on the frontend.</p>
     *
     * @param legalEntityId the entity whose CoA entries to return
     * @param accountType   account type filter (e.g. EXPENSE, ASSET, LIABILITY)
     * @return filtered list of CoA entries, empty if none found
     */
    List<ChartOfAccountDto> listByEntityAndType(UUID legalEntityId, AccountType accountType);
}
