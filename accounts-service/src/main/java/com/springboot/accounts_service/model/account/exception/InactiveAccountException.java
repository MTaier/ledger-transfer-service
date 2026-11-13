package com.springboot.accounts_service.model.account.exception;

public class InactiveAccountException extends RuntimeException {
    public InactiveAccountException() {
        super("Account not active.");
    }

    public InactiveAccountException(String message) {
        super(message);
    }
}
