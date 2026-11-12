package com.springboot.accounts_service.model.account.exception;

public class DocumentAlreadyRegisteredException extends RuntimeException{

    public DocumentAlreadyRegisteredException() {
        super("CPF already registered.");
    }
}
