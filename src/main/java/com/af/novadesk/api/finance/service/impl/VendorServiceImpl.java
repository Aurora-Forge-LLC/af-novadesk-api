package com.af.novadesk.api.finance.service.impl;

import com.af.novadesk.api.common.constants.Status;
import com.af.novadesk.api.finance.constants.VendorType;
import com.af.novadesk.api.finance.dto.UpdateVendorStatusRequest;
import com.af.novadesk.api.finance.dto.VendorDto;
import com.af.novadesk.api.finance.dto.VendorPageDto;
import com.af.novadesk.api.finance.entity.Account;
import com.af.novadesk.api.finance.entity.Vendor;
import com.af.novadesk.api.finance.exception.AccountNotFoundException;
import com.af.novadesk.api.finance.exception.DuplicateVendorException;
import com.af.novadesk.api.finance.exception.VendorNotFoundException;
import com.af.novadesk.api.finance.mapper.VendorMapper;
import com.af.novadesk.api.finance.repository.AccountRepository;
import com.af.novadesk.api.finance.repository.VendorRepository;
import com.af.novadesk.api.finance.security.FinanceSecurityContext;
import com.af.novadesk.api.finance.service.VendorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implements the Vendor Management service (LLR-FIN-03.3).
 *
 * <p>All operations are automatically scoped to the caller's {@code organizationId}
 * extracted from {@link FinanceSecurityContext} — never from the request body.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VendorServiceImpl implements VendorService {

    private final VendorRepository       vendorRepository;
    private final AccountRepository      accountRepository;
    private final FinanceSecurityContext  securityContext;
    private final VendorMapper           vendorMapper;

    // =========================================================================
    // Create
    // =========================================================================

    @Override
    @Transactional
    public VendorDto createVendor(VendorDto request) {
        UUID orgId = securityContext.getOrganizationId();

        if (vendorRepository.existsByVendorNameAndOrganizationId(request.getVendorName(), orgId)) {
            throw new DuplicateVendorException(request.getVendorName());
        }

        Account defaultAccount = resolveDefaultAccount(request.getDefaultAccountId());

        Vendor vendor = Vendor.builder()
                .organizationId(orgId)
                .vendorName(request.getVendorName())
                .vendorType(request.getVendorType())
                .taxId(request.getTaxId())
                .defaultAccount(defaultAccount)
                .build();

        Vendor saved = vendorRepository.save(vendor);
        log.info("Created vendor '{}' (id={}) for org={}", saved.getVendorName(), saved.getId(), orgId);

        return vendorMapper.toDto(saved);
    }

    // =========================================================================
    // Read
    // =========================================================================

    @Override
    public VendorPageDto listVendors(int page, int size, String sortBy, String sortDir,
                                     String q, VendorType vendorType, Status status) {
        UUID orgId = securityContext.getOrganizationId();
        Sort.Direction dir = "DESC".equalsIgnoreCase(sortDir) ? Sort.Direction.DESC : Sort.Direction.ASC;
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(dir, toEntityField(sortBy)));

        Page<Vendor> vendorPage = vendorRepository.findAll(
                VendorRepository.filterSpec(orgId, q, vendorType, status),
                pageRequest);

        List<VendorDto> content = vendorPage.getContent().stream()
                .map(vendorMapper::toDto)
                .collect(Collectors.toList());

        return new VendorPageDto(
                content,
                vendorPage.getNumber(),
                vendorPage.getSize(),
                vendorPage.getTotalElements(),
                vendorPage.getTotalPages()
        );
    }

    @Override
    public VendorDto getVendor(UUID id) {
        return vendorMapper.toDto(requireVendorInOrg(id));
    }

    // =========================================================================
    // Update
    // =========================================================================

    @Override
    @Transactional
    public VendorDto updateVendor(UUID id, VendorDto request) {
        UUID orgId = securityContext.getOrganizationId();
        Vendor vendor = requireVendorInOrg(id);

        // Check for duplicate name only if the name is actually changing
        if (!vendor.getVendorName().equals(request.getVendorName())) {
            if (vendorRepository.existsByVendorNameAndOrganizationIdAndIdNot(
                    request.getVendorName(), orgId, id)) {
                throw new DuplicateVendorException(request.getVendorName());
            }
        }

        Account defaultAccount = resolveDefaultAccount(request.getDefaultAccountId());

        vendor.setVendorName(request.getVendorName());
        vendor.setVendorType(request.getVendorType());
        vendor.setTaxId(request.getTaxId());
        vendor.setDefaultAccount(defaultAccount);

        // saveAndFlush forces the JPA flush inline so @PreUpdate / @LastModifiedDate
        // fires before toDto() reads updatedAt — otherwise the callback fires at
        // transaction commit AFTER the response is already built (stale timestamp).
        Vendor saved = vendorRepository.saveAndFlush(vendor);
        log.info("Updated vendor id={}", saved.getId());

        return vendorMapper.toDto(saved);
    }

    @Override
    @Transactional
    public VendorDto updateVendorStatus(UUID id, UpdateVendorStatusRequest request) {
        Vendor vendor = requireVendorInOrg(id);
        vendor.setStatus(request.getStatus());
        Vendor saved = vendorRepository.saveAndFlush(vendor);
        log.info("Vendor id={} status set to {}", saved.getId(), request.getStatus());
        return vendorMapper.toDto(saved);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    private Vendor requireVendorInOrg(UUID id) {
        return vendorRepository
                .findByIdAndOrganizationId(id, securityContext.getOrganizationId())
                .orElseThrow(() -> new VendorNotFoundException(id));
    }

    /**
     * Resolves the optional default account by UUID.
     * Returns null if no defaultAccountId is provided.
     * Throws {@link AccountNotFoundException} if the ID is provided but not found.
     */
    private Account resolveDefaultAccount(UUID defaultAccountId) {
        if (defaultAccountId == null) {
            return null;
        }
        return accountRepository.findById(defaultAccountId)
                .orElseThrow(() -> new AccountNotFoundException(
                        "Default account not found: " + defaultAccountId));
    }

    /**
     * Maps a sortBy request parameter to the corresponding entity field name.
     * Guards against arbitrary field injection into the ORDER BY clause.
     */
    private static String toEntityField(String sortBy) {
        return switch (sortBy == null ? "vendorName" : sortBy) {
            case "vendorType"  -> "vendorType";
            case "status"      -> "status";
            case "createdAt"   -> "createdAt";
            default            -> "vendorName";
        };
    }

}
