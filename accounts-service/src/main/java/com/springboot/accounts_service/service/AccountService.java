package com.springboot.accounts_service.service;

import com.springboot.accounts_service.infra.exception.AccountNotFoundException;
import com.springboot.accounts_service.infra.exception.DocumentAlreadyRegisteredException;
import com.springboot.accounts_service.infra.exception.InvalidDocumentException;
import com.springboot.accounts_service.model.Account;
import com.springboot.accounts_service.model.dto.DataAccountCreation;
import com.springboot.accounts_service.repository.AccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(DataAccountCreation data) {

        var document = normalizeDocument(data.document());

        if (!validateDocument(document)) {
            throw new InvalidDocumentException();
        }

        if (!validateDocumentUniqueness(document)) {
            throw new DocumentAlreadyRegisteredException();
        }

        var account = new Account(data.ownerName(), document);

        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            throw new DocumentAlreadyRegisteredException();
        }
    }

    public Page<Account> listAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable);
    }

    public Account fetchAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(AccountNotFoundException::new);
    }


    private String normalizeDocument(String document) {
        return document.replaceAll("\\D", "");
    }


    private boolean validateDocument(String document) {
        // Verifica se tem 11 dígitos ou se é sequência repetida
        if (document.length() != 11 || document.matches("^(\\d)\\1{10}$")) {
            return false;
        }

        char dig10, dig11;
        int sm, i, r, num, peso;

        try {
            // Cálculo do 10º dígito verificador
            sm = 0;
            peso = 10;
            for (i = 0; i < 9; i++) {
                num = (int)(document.charAt(i) - 48);
                sm = sm + (num * peso);
                peso = peso - 1;
            }

            r = 11 - (sm % 11);
            if ((r == 10) || (r == 11)) {
                dig10 = '0';
            } else {
                dig10 = (char)(r + 48);
            }

            // Cálculo do 11º dígito verificador
            sm = 0;
            peso = 11;
            for (i = 0; i < 10; i++) {
                num = (int)(document.charAt(i) - 48);
                sm = sm + (num * peso);
                peso = peso - 1;
            }

            r = 11 - (sm % 11);
            if ((r == 10) || (r == 11)) {
                dig11 = '0';
            } else {
                dig11 = (char)(r + 48);
            }

            // Verifica se os dígitos calculados conferem com os dígitos informados
            return (dig10 == document.charAt(9)) && (dig11 == document.charAt(10));
        } catch (Exception e) {
            return false;
        }
    }

    private boolean validateDocumentUniqueness(String document) {
        return !accountRepository.existsByDocument(document);
    }
}
