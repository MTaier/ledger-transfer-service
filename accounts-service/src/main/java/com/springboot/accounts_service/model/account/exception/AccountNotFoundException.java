package com.springboot.accounts_service.model.account.exception;

public class AccountNotFoundException extends RuntimeException {

    public AccountNotFoundException() {
        super("Account not found.");
    }

    public AccountNotFoundException(String message) {
        super(message);
    }
}
