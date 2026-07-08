package com.af.novadesk.api.finance.exception;

import com.af.novadesk.api.common.exception.FinanceBaseException;

/**
 * Thrown when a caller holds the {@code organizations:write} permission (needed
 * for legitimate entity-tier actions like inviting an entity-scoped user) but is
 * not an org-tier admin, and therefore may not create, approve, reject, or change
 * the status of a legal entity — those are ORG_ADMIN/SUPER_ADMIN/SYSTEM_ADMIN-only
 * actions regardless of what raw permission the caller's role happens to carry.
 */
public class LegalEntityManagementNotPermittedException extends FinanceBaseException {
    public LegalEntityManagementNotPermittedException(String action) {
        super("FIN_ACCESS_007",
                "Only an organization administrator can " + action + " a legal entity.");
    }
}
