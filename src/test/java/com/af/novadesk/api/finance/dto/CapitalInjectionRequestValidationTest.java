package com.af.novadesk.api.finance.dto;

import com.af.novadesk.api.finance.constants.FundingSource;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapitalInjectionRequestValidationTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        validatorFactory.close();
    }

    @Test
    void fundingDateInFuture_isRejectedByPastOrPresentConstraint() {
        CapitalInjectionRequest request = validRequest();
        request.setFundingDate(LocalDate.now().plusDays(1));

        Set<ConstraintViolation<CapitalInjectionRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> "fundingDate".equals(v.getPropertyPath().toString())));
        assertTrue(violations.stream()
                .anyMatch(v -> "fundingDate cannot be in the future".equals(v.getMessage())));
    }

    @Test
    void fundingDateToday_isAcceptedByPastOrPresentConstraint() {
        CapitalInjectionRequest request = validRequest();
        request.setFundingDate(LocalDate.now());

        Set<ConstraintViolation<CapitalInjectionRequest>> violations = validator.validate(request);

        assertFalse(violations.stream().anyMatch(v -> "fundingDate".equals(v.getPropertyPath().toString())));
    }

    @Test
    void manualExchangeRateZero_isRejectedByDecimalMinConstraint() {
        CapitalInjectionRequest request = validRequest();
        request.setManualExchangeRate(BigDecimal.ZERO);

        Set<ConstraintViolation<CapitalInjectionRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> "manualExchangeRate".equals(v.getPropertyPath().toString())));
        assertTrue(violations.stream()
                .anyMatch(v -> "manualExchangeRate must be a positive value".equals(v.getMessage())));
    }

    @Test
    void manualExchangeRateTooManyFractionDigits_isRejectedByDigitsConstraint() {
        CapitalInjectionRequest request = validRequest();
        request.setManualExchangeRate(new BigDecimal("1.1234567"));

        Set<ConstraintViolation<CapitalInjectionRequest>> violations = validator.validate(request);

        assertTrue(violations.stream().anyMatch(v -> "manualExchangeRate".equals(v.getPropertyPath().toString())));
        assertTrue(violations.stream().anyMatch(
                v -> "manualExchangeRate must have at most 10 integer and 6 decimal digits".equals(v.getMessage())));
    }

    private CapitalInjectionRequest validRequest() {
        CapitalInjectionRequest request = new CapitalInjectionRequest();
        request.setTargetEntityCode("INDIA");
        request.setFundingSource(FundingSource.FOUNDER_EQUITY);
        request.setAmount(new BigDecimal("1000.0000"));
        request.setFundingDate(LocalDate.now());
        request.setSourceAccountId(UUID.randomUUID());
        return request;
    }
}

