package com.springboot.accounts_service.model.transfer.exception;

public class TransferNotFoundException extends RuntimeException {
    public TransferNotFoundException() {
        super("Transfer not found.");
    }
}
