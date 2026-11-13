package com.springboot.accounts_service.model.transfer.exception;

public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException() {
        super("Insufficient funds to complete this transfer.");
    }
}
