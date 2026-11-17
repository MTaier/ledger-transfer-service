package com.springboot.accounts_service.model.transfer.dto;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class DepositEntryTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("Rejects a deposit amount equal to zero")
    void shouldRejectZeroAmount() {
        var deposit = new DepositEntry(new BigDecimal("0.00"));

        var violations = validator.validate(deposit);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    @DisplayName("Rejects a negative deposit amount")
    void shouldRejectNegativeAmount() {
        var deposit = new DepositEntry(new BigDecimal("-10.00"));

        var violations = validator.validate(deposit);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    @DisplayName("Rejects a deposit amount with more than 2 decimal places")
    void shouldRejectAmountWithThreeDecimals() {
        var deposit = new DepositEntry(new BigDecimal("10.005"));

        var violations = validator.validate(deposit);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    @DisplayName("Accepts a deposit amount with exactly 2 decimal places")
    void shouldAcceptAmountWithTwoDecimals() {
        var deposit = new DepositEntry(new BigDecimal("10.05"));

        var violations = validator.validate(deposit);

        assertThat(violations).isEmpty();
    }
}
