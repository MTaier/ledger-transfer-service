package com.springboot.accounts_service.model.transfer.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record TransferEntry(

        @NotNull
        Long fromAccountId,

        @NotNull
        Long toAccountId,

        @NotNull
        @Positive
        @Digits(integer = 17, fraction = 2)
        BigDecimal amount) {
}
