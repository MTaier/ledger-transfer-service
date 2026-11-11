package com.springboot.accounts_service.infra.exception;

public class TransferNotFoundException extends RuntimeException{
    public TransferNotFoundException() {
        super("Transfer not found.");
    }
}
