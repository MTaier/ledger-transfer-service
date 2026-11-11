package com.springboot.accounts_service.service;

import com.springboot.accounts_service.infra.exception.AccountNotFoundException;
import com.springboot.accounts_service.infra.exception.DocumentAlreadyRegisteredException;
import com.springboot.accounts_service.infra.exception.InvalidDocumentException;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.dto.DataAccountCreation;
import com.springboot.accounts_service.repository.AccountRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldCreateAccountSuccessfully() {
        var data = new DataAccountCreation(
                "John Doe",
                "88461276094"
        );

        when(accountRepository.existsByDocument("88461276094"))
                .thenReturn(false);

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = accountService.createAccount(data);

        assertThat(result).isNotNull();
        assertThat(result.getOwnerName()).isEqualTo("John Doe");
        assertThat(result.getDocument()).isEqualTo("88461276094");

        verify(accountRepository).existsByDocument("88461276094");
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void shouldRejectInvalidCpf() {
        var data = new DataAccountCreation(
                "John Doe",
                "884.612.760-95"
        );

        assertThatThrownBy(() -> accountService.createAccount(data))
                .isInstanceOf(InvalidDocumentException.class);

        verify(accountRepository, never()).existsByDocument(anyString());
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void shouldRejectDuplicateCpf() {
        var data = new DataAccountCreation(
                "John Doe",
                "884.612.760-94"
        );

        when(accountRepository.existsByDocument("88461276094"))
                .thenReturn(true);

        assertThatThrownBy(() -> accountService.createAccount(data))
                .isInstanceOf(DocumentAlreadyRegisteredException.class);

        verify(accountRepository).existsByDocument("88461276094");
        verify(accountRepository, never()).saveAndFlush(any(Account.class));
    }

    @Test
    void shouldRejectDuplicateCpfOnConcurrentInsert() {
        var data = new DataAccountCreation(
                "John Doe",
                "884.612.760-94"
        );

        when(accountRepository.existsByDocument("88461276094"))
                .thenReturn(false);

        var constraintViolation = new ConstraintViolationException(
                "Duplicate document",
                new SQLException(),
                "uk_document"
        );

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "Unique constraint violation",
                        constraintViolation
                ));

        assertThatThrownBy(() -> accountService.createAccount(data))
                .isInstanceOf(DocumentAlreadyRegisteredException.class);

        verify(accountRepository).existsByDocument("88461276094");
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

    @Test
    void shouldThrowWhenAccountDoesNotExist() {
        Long accountId = 999L;

        when(accountRepository.findById(accountId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> accountService.fetchAccount(accountId))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository).findById(accountId);
    }

    @Test
    void shouldNotTreatOtherIntegrityViolationAsDuplicateDocument() {
        var data = new DataAccountCreation(
                "John Doe",
                "884.612.760-94"
        );

        when(accountRepository.existsByDocument("88461276094"))
                .thenReturn(false);

        var constraintViolation = new ConstraintViolationException(
                "Not null constraint violation",
                new SQLException(),
                "uk_owner_name"
        );

        var integrityViolation = new DataIntegrityViolationException(
                "Integrity constraint violation",
                constraintViolation
        );

        when(accountRepository.saveAndFlush(any(Account.class)))
                .thenThrow(integrityViolation);

        assertThatThrownBy(() -> accountService.createAccount(data))
                .isSameAs(integrityViolation);

        verify(accountRepository).existsByDocument("88461276094");
        verify(accountRepository).saveAndFlush(any(Account.class));
    }

}
