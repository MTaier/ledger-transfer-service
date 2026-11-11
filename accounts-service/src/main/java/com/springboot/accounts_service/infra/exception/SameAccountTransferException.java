package com.springboot.accounts_service.infra.exception;

public class SameAccountTransferException extends RuntimeException {
    public SameAccountTransferException() {
        super("Source and destination accounts must be different.");
    }
}
