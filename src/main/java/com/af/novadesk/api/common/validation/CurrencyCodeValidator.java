package com.af.novadesk.api.common.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.util.Currency;

public class CurrencyCodeValidator implements ConstraintValidator<ValidCurrencyCode, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true; // defer to @NotBlank
        }
        try {
            Currency.getInstance(value.trim().toUpperCase());
            return true;
        } catch (IllegalArgumentException e) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    "'" + value.trim().toUpperCase() + "' is not a valid ISO 4217 currency code"
            ).addConstraintViolation();
            return false;
        }
    }
}
