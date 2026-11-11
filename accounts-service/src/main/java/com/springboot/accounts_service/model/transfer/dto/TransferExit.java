package com.springboot.accounts_service.model.transfer.dto;

import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TransferExit(
        Long id,
        Long fromAccountId,
        Long toAccountId,
        BigDecimal amount,
        TransferStatus status,
        LocalDateTime createdAt) {

    public TransferExit(Transfer transfer) {
        this(transfer.getId(), transfer.getFromAccountId(), transfer.getToAccountId(), transfer.getAmount(), transfer.getStatus(), transfer.getCreatedAt());
    }
}
