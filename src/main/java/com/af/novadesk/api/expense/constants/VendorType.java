package com.af.novadesk.api.expense.constants;

/**
 * Business category of a {@link com.af.novadesk.api.expense.entity.Vendor}.
 *
 * <p>Used to classify vendors on the vendor quick-add form (LLR-FIN-03.3)
 * and to drive reporting groupings. Stored as a string enum on the
 * {@code Vendor} entity — no separate lookup table needed.</p>
 */
public enum VendorType {

    /** Software-as-a-Service, cloud, or professional service providers (e.g. AWS, Jira). */
    SERVICE_PROVIDER,

    /** Office or facility landlord. */
    LANDLORD,

    /** Electricity, water, internet, phone, or similar utility providers. */
    UTILITY,

    /** Goods or raw material suppliers. */
    SUPPLIER,

    /** Independent contractors or freelancers. */
    CONTRACTOR,

    /** Government agencies, tax bodies, regulatory bodies. */
    GOVERNMENT,

    /** Any vendor that does not fit the above categories. */
    OTHER
}
