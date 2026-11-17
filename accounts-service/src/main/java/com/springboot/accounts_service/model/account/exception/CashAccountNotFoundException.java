package com.springboot.accounts_service.model.account.exception;

public class CashAccountNotFoundException extends RuntimeException {
    public CashAccountNotFoundException() {
        super("The bank's internal CASH account could not be found.");
    }
}
