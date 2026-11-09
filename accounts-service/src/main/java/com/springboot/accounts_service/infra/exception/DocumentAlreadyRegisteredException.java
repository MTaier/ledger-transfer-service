package com.springboot.accounts_service.infra.exception;

public class DocumentAlreadyRegisteredException extends RuntimeException{

    public DocumentAlreadyRegisteredException() {
        super("CPF already registered.");
    }
}
