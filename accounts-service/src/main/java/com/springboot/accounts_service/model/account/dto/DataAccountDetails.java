package com.springboot.accounts_service.model.account.dto;

import com.springboot.accounts_service.model.account.Account;

public record DataAccountDetails(Long id, String ownerName, String document) {

    public DataAccountDetails(Account account) {
        this(account.getId(), account.getOwnerName(), maskDocument(account.getDocument()));
    }

    private static String maskDocument(String document) {
        return "***." + document.substring(3, 6)
                + "." + document.substring(6, 9)
                + "-**";
    }

}
