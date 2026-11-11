package com.springboot.accounts_service.model.transfer;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity(name = "Transfer")
@Table(name = "transfers")
public class Transfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String idempotencyKey;

    @NotNull
    @Column(name = "from_account_id")
    private Long fromAccountId;

    @NotNull
    @Column(name = "to_account_id")
    private Long toAccountId;

    @NotNull
    @Positive
    @Column(precision = 19, scale = 2)
    private BigDecimal amount;

    @NotNull
    @Enumerated(EnumType.STRING)
    private TransferStatus status;

    @NotNull
    private LocalDateTime createdAt;

    protected Transfer() {
    }

    public Transfer(Long fromAccountId, Long toAccountId, BigDecimal amount, TransferStatus status) {
        this.fromAccountId = fromAccountId;
        this.toAccountId = toAccountId;
        this.amount = amount;
        this.status = status;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Long getFromAccountId() {
        return fromAccountId;
    }

    public Long getToAccountId() {
        return toAccountId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransferStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

}
