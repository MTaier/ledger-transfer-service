package com.springboot.accounts_service.model.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CpfUtilsTest {

    @Test
    @DisplayName("Accepts a CPF with valid check digits")
    void shouldAcceptValidCpf() {
        assertThat(CpfUtils.isValid("12345678909")).isTrue();
    }

    @Test
    @DisplayName("Rejects a CPF with all digits equal")
    void shouldRejectCpfWithAllSameDigits() {
        assertThat(CpfUtils.isValid("11111111111")).isFalse();
    }

    @Test
    @DisplayName("Rejects a CPF with wrong check digits")
    void shouldRejectCpfWithWrongCheckDigits() {
        assertThat(CpfUtils.isValid("12345678900")).isFalse();
    }

    @Test
    @DisplayName("Rejects a CPF that does not have 11 digits")
    void shouldRejectCpfWithWrongLength() {
        assertThat(CpfUtils.isValid("123456789")).isFalse();
        assertThat(CpfUtils.isValid("123456789000")).isFalse();
    }

    @Test
    @DisplayName("Removes dots and dash from a masked CPF")
    void shouldRemoveMaskFromCpf() {
        assertThat(CpfUtils.normalizeDocument("123.456.789-09")).isEqualTo("12345678909");
    }

    @Test
    @DisplayName("Keeps an unmasked CPF unchanged")
    void shouldKeepUnmaskedCpfUnchanged() {
        assertThat(CpfUtils.normalizeDocument("12345678909")).isEqualTo("12345678909");
    }

}
