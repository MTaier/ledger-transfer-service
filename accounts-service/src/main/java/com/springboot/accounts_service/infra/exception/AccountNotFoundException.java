package com.springboot.accounts_service.infra.exception;

public class AccountNotFoundException extends RuntimeException{

    public AccountNotFoundException() {
        super("Account not found.");
    }
}
