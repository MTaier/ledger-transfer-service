package com.springboot.accounts_service.model.validation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class CpfUtilsTest {

    @Test
    void shouldAcceptValidCpf() {
        assertThat(CpfUtils.isValid("12345678909")).isTrue();
    }

    @Test
    void shouldRejectCpfWithAllSameDigits() {
        assertThat(CpfUtils.isValid("11111111111")).isFalse();
    }

    @Test
    void shouldRejectCpfWithWrongCheckDigits() {
        assertThat(CpfUtils.isValid("12345678900")).isFalse();
    }

    @Test
    void shouldRejectCpfWithWrongLength() {
        assertThat(CpfUtils.isValid("123456789")).isFalse();
        assertThat(CpfUtils.isValid("123456789000")).isFalse();
    }

    @Test
    void shouldRemoveMaskFromCpf() {
        assertThat(CpfUtils.normalizeDocument("123.456.789-09")).isEqualTo("12345678909");
    }

    @Test
    void shouldKeepUnmaskedCpfUnchanged() {
        assertThat(CpfUtils.normalizeDocument("12345678909")).isEqualTo("12345678909");
    }

}
