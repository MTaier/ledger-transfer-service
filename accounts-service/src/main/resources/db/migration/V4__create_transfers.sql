CREATE TABLE transfers
(
    id              BIGINT         NOT NULL AUTO_INCREMENT,
    idempotency_key VARCHAR(64)    NULL,
    from_account_id BIGINT         NOT NULL,
    to_account_id   BIGINT         NOT NULL,
    amount          DECIMAL(19, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    created_at      TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_key (idempotency_key),

    CONSTRAINT fk_transfers_from_account FOREIGN KEY (from_account_id) REFERENCES accounts (id),
    CONSTRAINT fk_transfers_to_account FOREIGN KEY (to_account_id) REFERENCES accounts (id),

    CONSTRAINT ck_transfers_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfers_different_accounts CHECK (from_account_id <> to_account_id)
);