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

class TransferEntryTest {

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
    @DisplayName("Rejects an amount equal to zero")
    void shouldRejectZeroAmount() {
        var entry = new TransferEntry(1L, 2L, new BigDecimal("0.00"));

        var violations = validator.validate(entry);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    @DisplayName("Rejects a negative amount")
    void shouldRejectNegativeAmount() {
        var entry = new TransferEntry(1L, 2L, new BigDecimal("-10.00"));

        var violations = validator.validate(entry);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    @DisplayName("Rejects an amount with more than 2 decimal places")
    void shouldRejectAmountWithThreeDecimals() {
        var entry = new TransferEntry(1L, 2L, new BigDecimal("10.005"));

        var violations = validator.validate(entry);

        assertThat(violations)
                .extracting(v -> v.getPropertyPath().toString())
                .containsExactly("amount");
    }

    @Test
    @DisplayName("Accepts an amount with exactly 2 decimal places")
    void shouldAcceptAmountWithTwoDecimals() {
        var entry = new TransferEntry(1L, 2L, new BigDecimal("10.00"));

        var violations = validator.validate(entry);

        assertThat(violations).isEmpty();
    }
}
