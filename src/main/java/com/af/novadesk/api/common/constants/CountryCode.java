package com.af.novadesk.api.common.constants;

/**
 * Supported countries for legal entity registration.
 * Each entry carries its ISO alpha-2 code and default base currency.
 */
public enum CountryCode {

    US("USD"),
    IN("INR"),
    NP("NPR");

    private final String defaultCurrencyCode;

    CountryCode(String defaultCurrencyCode) {
        this.defaultCurrencyCode = defaultCurrencyCode;
    }

    public String getDefaultCurrencyCode() {
        return defaultCurrencyCode;
    }
}
