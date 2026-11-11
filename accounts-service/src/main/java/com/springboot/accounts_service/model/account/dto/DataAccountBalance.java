package com.springboot.accounts_service.model.account.dto;

import java.math.BigDecimal;

public record DataAccountBalance(Long accountId, BigDecimal balance) {
}
