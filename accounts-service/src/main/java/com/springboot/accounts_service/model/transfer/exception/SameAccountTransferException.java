package com.springboot.accounts_service.model.transfer.exception;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException() {
        super("Source and destination accounts must be different.");
    }
}
