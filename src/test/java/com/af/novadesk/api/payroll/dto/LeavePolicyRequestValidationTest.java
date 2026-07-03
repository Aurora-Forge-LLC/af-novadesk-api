package com.af.novadesk.api.payroll.dto;

import com.af.novadesk.api.payroll.constants.LeavePaymentType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LeavePolicyRequestValidationTest {

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
    @DisplayName("negative allowedDays is rejected — prevents 500 from reaching the service")
    void negativeAllowedDays_isRejected() {
        LeavePolicyRequest request = validRequest();
        request.setAllowedDays(-1);

        Set<ConstraintViolation<LeavePolicyRequest>> violations = validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(v -> "allowedDays".equals(v.getPropertyPath().toString())));
        assertTrue(violations.stream()
                .anyMatch(v -> "Allowed days must be 0 or greater".equals(v.getMessage())));
    }

    @Test
    @DisplayName("zero allowedDays is accepted — valid for unlimited-type policies")
    void zeroAllowedDays_isAccepted() {
        LeavePolicyRequest request = validRequest();
        request.setAllowedDays(0);

        Set<ConstraintViolation<LeavePolicyRequest>> violations = validator.validate(request);

        assertFalse(violations.stream()
                .anyMatch(v -> "allowedDays".equals(v.getPropertyPath().toString())));
    }

    @Test
    @DisplayName("null allowedDays is rejected by NotNull")
    void nullAllowedDays_isRejected() {
        LeavePolicyRequest request = validRequest();
        request.setAllowedDays(null);

        Set<ConstraintViolation<LeavePolicyRequest>> violations = validator.validate(request);

        assertTrue(violations.stream()
                .anyMatch(v -> "allowedDays".equals(v.getPropertyPath().toString())));
        assertTrue(violations.stream()
                .anyMatch(v -> "Allowed days is required".equals(v.getMessage())));
    }

    @Test
    @DisplayName("positive allowedDays is accepted")
    void positiveAllowedDays_isAccepted() {
        LeavePolicyRequest request = validRequest();
        request.setAllowedDays(20);

        Set<ConstraintViolation<LeavePolicyRequest>> violations = validator.validate(request);

        assertFalse(violations.stream()
                .anyMatch(v -> "allowedDays".equals(v.getPropertyPath().toString())));
    }

    private LeavePolicyRequest validRequest() {
        LeavePolicyRequest request = new LeavePolicyRequest();
        request.setLegalEntityId(UUID.randomUUID());
        request.setName("Annual Leave");
        request.setPaymentType(LeavePaymentType.PAID);
        request.setAllowedDays(20);
        request.setIsUnlimited(false);
        request.setIsEarned(false);
        return request;
    }
}
