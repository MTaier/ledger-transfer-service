package com.springboot.accounts_service.infra.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException() {
        super("Insufficient balance.");
    }
}
