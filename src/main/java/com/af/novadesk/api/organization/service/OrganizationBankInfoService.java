package com.af.novadesk.api.organization.service;

import com.af.novadesk.api.organization.dto.BankAccountBalanceResponse;
import com.af.novadesk.api.organization.dto.OrganizationBankCreditRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoResponse;

import java.util.List;
import java.util.UUID;

/**
 * Service interface for managing organization bank account information.
 *
 * <p>All operations are scoped to the authenticated user's organization
 * and require the {@code Super_admin} authority.</p>
 */
public interface OrganizationBankInfoService {

    /**
     * Create a new bank account record for the organization.
     *
     * @param orgId  the authenticated user's organization ID
     * @param userId the authenticated Super_admin user's ID
     * @param request the bank account details
     * @return the created bank info response
     */
    OrganizationBankInfoResponse create(UUID orgId, UUID userId, OrganizationBankInfoRequest request);

    /**
     * Retrieve a single bank account record by its ID, scoped to the organization.
     *
     * @param orgId the authenticated user's organization ID
     * @param id    the bank account record ID
     * @return the bank info response
     * @throws com.af.novadesk.api.finance.exception.NotFoundException if not found
     */
    OrganizationBankInfoResponse getById(UUID orgId, UUID id);

    /**
     * List all bank account records for the organization.
     *
     * @param orgId the authenticated user's organization ID
     * @return list of bank info responses
     */
    List<OrganizationBankInfoResponse> list(UUID orgId);

    /**
     * Update an existing bank account record.
     *
     * @param orgId  the authenticated user's organization ID
     * @param userId the authenticated Super_admin user's ID
     * @param id     the bank account record ID
     * @param request the updated bank account details
     * @return the updated bank info response
     */
    OrganizationBankInfoResponse update(UUID orgId, UUID userId, UUID id, OrganizationBankInfoRequest request);

    /**
     * Soft-delete (set status to DELETED) a bank account record.
     *
     * @param orgId  the authenticated user's organization ID
     * @param userId the authenticated Super_admin user's ID
     * @param id     the bank account record ID
     */
    void delete(UUID orgId, UUID userId, UUID id);

    // ── Balance Operations ──────────────────────────────────────────────────

    /**
     * Credit (deposit) funds into an organization bank account, increasing
     * its balance.  This represents loading money into the account from which
     * capital injections and entity transfers can be made.
     *
     * @param orgId   the authenticated user's organization ID
     * @param userId  the authenticated Super_admin user's ID
     * @param id      the bank account record ID
     * @param request contains the positive amount to add
     * @return the updated balance response
     * @throws com.af.novadesk.api.finance.exception.NotFoundException if the bank info is not found
     */
    BankAccountBalanceResponse creditBalance(UUID orgId, UUID userId, UUID id, OrganizationBankCreditRequest request);

    /**
     * Debit (withdraw) funds from an organization bank account, decreasing
     * its balance.  This is used when transferring money out of the
     * organization bank account to fund capital injections or entity
     * transfers.
     *
     * <p>A check is performed to ensure sufficient funds are available
     * before deducting.</p>
     *
     * @param orgId   the authenticated user's organization ID
     * @param userId  the authenticated Super_admin user's ID
     * @param id      the bank account record ID
     * @param request contains the positive amount to subtract
     * @return the updated balance response
     * @throws com.af.novadesk.api.finance.exception.NotFoundException if the bank info is not found
     * @throws IllegalStateException if insufficient funds
     */
    BankAccountBalanceResponse debitBalance(UUID orgId, UUID userId, UUID id, OrganizationBankCreditRequest request);
}
