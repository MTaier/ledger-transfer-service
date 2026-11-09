package com.springboot.accounts_service.service;

import com.springboot.accounts_service.infra.exception.AccountNotFoundException;
import com.springboot.accounts_service.infra.exception.DocumentAlreadyRegisteredException;
import com.springboot.accounts_service.infra.exception.InvalidDocumentException;
import com.springboot.accounts_service.model.Account;
import com.springboot.accounts_service.model.dto.DataAccountCreation;
import com.springboot.accounts_service.model.validation.CpfUtils;
import com.springboot.accounts_service.repository.AccountRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Account createAccount(DataAccountCreation data) {

        var document = CpfUtils.normalizeDocument(data.document());

        if (!CpfUtils.isValid(document)) {
            throw new InvalidDocumentException();
        }

        if (!validateDocumentUniqueness(document)) {
            throw new DocumentAlreadyRegisteredException();
        }

        var account = new Account(data.ownerName(), document);

        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            if (isDocumentUniqueViolation(e)) {
                throw new DocumentAlreadyRegisteredException();
            }
            throw e;
        }
    }

    public Page<Account> listAllAccounts(Pageable pageable) {
        return accountRepository.findAll(pageable);
    }

    public Account fetchAccount(Long id) {
        return accountRepository.findById(id)
                .orElseThrow(AccountNotFoundException::new);
    }


    private boolean validateDocumentUniqueness(String document) {
        return !accountRepository.existsByDocument(document);
    }

    private boolean isDocumentUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException cve
                && cve.getConstraintName() != null
                && cve.getConstraintName().contains("uk_document");
    }
}
