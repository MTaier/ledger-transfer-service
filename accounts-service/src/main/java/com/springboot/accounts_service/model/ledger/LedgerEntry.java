package com.springboot.accounts_service.model.ledger;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "LedgerEntry")
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull
    @Column(name = "transfer_id")
    private Long transferId;

    @NotNull
    @Column(name = "account_id")
    private Long accountId;

    @NotNull
    @Enumerated(EnumType.STRING)
    private LedgerEntryDirection direction;

    @NotNull
    @Positive
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    private LocalDateTime createdAt;

    protected LedgerEntry() {
    }

    public LedgerEntry(Long transferId, Long accountId, LedgerEntryDirection direction, BigDecimal amount) {
        this.transferId = transferId;
        this.accountId = accountId;
        this.direction = direction;
        this.amount = amount;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getTransferId() {
        return transferId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public LedgerEntryDirection getDirection() {
        return direction;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
