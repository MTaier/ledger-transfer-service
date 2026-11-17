ALTER TABLE accounts
    ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER' AFTER document,
    MODIFY COLUMN document VARCHAR(14) NULL,
    ADD CONSTRAINT ck_accounts_type CHECK (account_type IN ('CUSTOMER', 'CASH')),
    ADD CONSTRAINT ck_accounts_customer_has_document
        CHECK (account_type <> 'CUSTOMER' OR document IS NOT NULL);

INSERT INTO accounts (owner_name, document, account_type, status)
VALUES ('Cash account', NULL, 'CASH', 'ACTIVE');