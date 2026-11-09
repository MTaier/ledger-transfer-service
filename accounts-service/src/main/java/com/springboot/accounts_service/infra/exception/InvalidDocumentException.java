package com.springboot.accounts_service.infra.exception;

public class InvalidDocumentException extends RuntimeException{

    public InvalidDocumentException() {
        super ("Invalid CPF.");
    }
}
