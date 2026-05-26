package com.af.novadesk.api.organization.service.impl;

import com.af.novadesk.api.common.constants.ApiMessages;
import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.exception.NotFoundException;
import com.af.novadesk.api.organization.dto.BankAccountBalanceResponse;
import com.af.novadesk.api.organization.dto.OrganizationBankCreditRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoRequest;
import com.af.novadesk.api.organization.dto.OrganizationBankInfoResponse;
import com.af.novadesk.api.organization.entity.OrganizationBankInfo;
import com.af.novadesk.api.organization.mapper.OrganizationBankInfoMapper;
import com.af.novadesk.api.organization.repository.OrganizationBankInfoRepository;
import com.af.novadesk.api.organization.service.OrganizationBankInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

/**
 * Implementation of {@link OrganizationBankInfoService}.
 *
 * <p>Handles the business logic for organization bank account CRUD operations
 * and balance management. All mutations are transactional and ensure data
 * integrity constraints (e.g. unique account number per organization, single
 * primary account, non-negative balance).</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrganizationBankInfoServiceImpl implements OrganizationBankInfoService {

    private final OrganizationBankInfoRepository repository;
    private final OrganizationBankInfoMapper mapper;

    // ── Create ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrganizationBankInfoResponse create(UUID orgId, UUID userId, OrganizationBankInfoRequest request) {
        log.debug("Creating bank info for orgId={} by userId={}", orgId, userId);

        // Enforce unique account number per organization
        if (repository.existsByOrgIdAndAccountNumber(orgId, request.getAccountNumber())) {
            throw new com.af.novadesk.api.finance.exception.DuplicateEntityException(
                    "accountNumber", request.getAccountNumber());
        }

        // Map request to entity and set server-managed fields
        OrganizationBankInfo entity = mapper.toEntity(request);
        entity.setOrgId(orgId);
        entity.setUserId(userId);

        // Handle primary account logic: if setting as primary, reset others
        if (request.isPrimary()) {
            repository.resetPrimaryForOrganization(orgId);
        }

        OrganizationBankInfo saved = repository.save(entity);
        log.info("Created bank info id={} for orgId={}", saved.getId(), orgId);
        return mapper.toResponse(saved);
    }

    // ── Read ────────────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public OrganizationBankInfoResponse getById(UUID orgId, UUID id) {
        log.debug("Fetching bank info id={} for orgId={}", id, orgId);
        OrganizationBankInfo entity = findByIdAndOrgId(orgId, id);
        return mapper.toResponse(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OrganizationBankInfoResponse> list(UUID orgId) {
        log.debug("Listing bank info for orgId={}", orgId);
        List<OrganizationBankInfo> entities = repository.findByOrgIdOrderByCreatedAtDesc(orgId);
        return mapper.toResponseList(entities);
    }

    // ── Update ──────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public OrganizationBankInfoResponse update(UUID orgId, UUID userId, UUID id, OrganizationBankInfoRequest request) {
        log.debug("Updating bank info id={} for orgId={} by userId={}", id, orgId, userId);

        OrganizationBankInfo entity = findByIdAndOrgId(orgId, id);

        // If account number is being changed, check uniqueness
        if (!entity.getAccountNumber().equals(request.getAccountNumber())) {
            if (repository.existsByOrgIdAndAccountNumberAndIdNot(orgId, request.getAccountNumber(), id)) {
                throw new com.af.novadesk.api.finance.exception.DuplicateEntityException(
                        "accountNumber", request.getAccountNumber());
            }
        }

        // Handle primary account logic
        if (request.isPrimary() && !entity.isPrimary()) {
            repository.resetPrimaryForOrganization(orgId);
        }

        // Update the user ID to track who last modified it
        entity.setUserId(userId);

        mapper.updateEntity(entity, request);
        OrganizationBankInfo saved = repository.save(entity);
        log.info("Updated bank info id={} for orgId={}", saved.getId(), orgId);
        return mapper.toResponse(saved);
    }

    // ── Delete (Soft) ───────────────────────────────────────────────────────────

    @Override
    @Transactional
    public void delete(UUID orgId, UUID userId, UUID id) {
        log.debug("Soft-deleting bank info id={} for orgId={} by userId={}", id, orgId, userId);

        OrganizationBankInfo entity = findByIdAndOrgId(orgId, id);
        entity.setStatus(Status.DELETED);
        entity.setUserId(userId);
        repository.save(entity);

        log.info("Soft-deleted bank info id={} for orgId={}", id, orgId);
    }

    // ── Balance Operations ──────────────────────────────────────────────────────

    @Override
    @Transactional
    public BankAccountBalanceResponse creditBalance(UUID orgId, UUID userId, UUID id,
                                                    OrganizationBankCreditRequest request) {
        log.debug("Crediting bank info id={} for orgId={} by userId={}, amount={}",
                id, orgId, userId, request.getAmount());

        OrganizationBankInfo entity = findByIdAndOrgId(orgId, id);

        BigDecimal amount = scale(request.getAmount());
        BigDecimal newBalance = entity.getBalance().add(amount);
        entity.setBalance(newBalance);
        entity.setUserId(userId);

        OrganizationBankInfo saved = repository.save(entity);
        log.info("Credited {} to bank info id={}, new balance={}", amount, id, saved.getBalance());

        return new BankAccountBalanceResponse(saved.getId(), saved.getBalance());
    }

    @Override
    @Transactional
    public BankAccountBalanceResponse debitBalance(UUID orgId, UUID userId, UUID id,
                                                   OrganizationBankCreditRequest request) {
        log.debug("Debiting bank info id={} for orgId={} by userId={}, amount={}",
                id, orgId, userId, request.getAmount());

        OrganizationBankInfo entity = findByIdAndOrgId(orgId, id);

        BigDecimal amount = scale(request.getAmount());

        // Check sufficient funds
        if (entity.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException(
                    ApiMessages.INSUFFICIENT_FUNDS + " — available: " + entity.getBalance()
                            + ", requested: " + amount);
        }

        BigDecimal newBalance = entity.getBalance().subtract(amount);
        entity.setBalance(newBalance);
        entity.setUserId(userId);

        OrganizationBankInfo saved = repository.save(entity);
        log.info("Debited {} from bank info id={}, new balance={}", amount, id, saved.getBalance());

        return new BankAccountBalanceResponse(saved.getId(), saved.getBalance());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    private OrganizationBankInfo findByIdAndOrgId(UUID orgId, UUID id) {
        return repository.findByOrgIdAndId(orgId, id)
                .orElseThrow(() -> new NotFoundException(
                        "Organization bank info not found with id: " + id + " for orgId: " + orgId));
    }

    /**
     * Scale a {@link BigDecimal} amount to 4 decimal places using {@link RoundingMode#HALF_UP},
     * matching the column definition {@code DECIMAL(19,4)}.
     */
    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }
}
