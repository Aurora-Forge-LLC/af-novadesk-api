package com.af.novadesk.api.finance.service;

import com.af.novadesk.api.finance.dto.UpdateVendorStatusRequest;
import com.af.novadesk.api.finance.dto.VendorDto;
import com.af.novadesk.api.finance.dto.VendorPageDto;

import java.util.UUID;

/**
 * Service contract for Vendor Management (LLR-FIN-03.3).
 *
 * <p>Vendors are scoped to the caller's {@code organizationId} — extracted from
 * the JWT on every call. No vendor data crosses organizational boundaries.</p>
 */
public interface VendorService {

    /**
     * Registers a new vendor in the caller's organization.
     * Throws {@link com.af.novadesk.api.finance.exception.DuplicateVendorException}
     * if a vendor with the same name already exists in the org.
     */
    VendorDto createVendor(VendorDto request);

    /**
     * Returns a paginated, optionally name-filtered list of vendors.
     *
     * @param page    0-based page index
     * @param size    number of records per page
     * @param sortBy  field name to sort by (e.g. "vendorName")
     * @param search  optional name fragment for autocomplete — {@code null} returns all
     */
    VendorPageDto listVendors(int page, int size, String sortBy, String search);

    /**
     * Returns a single vendor by UUID.
     * Throws {@link com.af.novadesk.api.finance.exception.VendorNotFoundException}
     * if not found or belongs to a different organization.
     */
    VendorDto getVendor(UUID id);

    /**
     * Updates a vendor's name, type, tax ID, or default account.
     * Throws {@link com.af.novadesk.api.finance.exception.DuplicateVendorException}
     * if the new name conflicts with another vendor in the org.
     */
    VendorDto updateVendor(UUID id, VendorDto request);

    /**
     * Activates or deactivates a vendor.
     * Inactive vendors are hidden from the expense form autocomplete.
     */
    VendorDto updateVendorStatus(UUID id, UpdateVendorStatusRequest request);
}
