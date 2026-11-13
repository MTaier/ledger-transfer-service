package com.springboot.accounts_service.model.account.exception;

public class InvalidDocumentException extends RuntimeException {

    public InvalidDocumentException() {
        super("Invalid CPF.");
    }
}
