package com.springboot.accounts_service.service;

import com.springboot.accounts_service.model.account.AccountStatus;
import com.springboot.accounts_service.model.account.exception.AccountNotFoundException;
import com.springboot.accounts_service.model.account.exception.InactiveAccountException;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import com.springboot.accounts_service.model.transfer.dto.TransferEntry;
import com.springboot.accounts_service.model.transfer.exception.InsufficientBalanceException;
import com.springboot.accounts_service.model.transfer.exception.SameAccountTransferException;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension .class)
public class TransferServiceTest {

    @Mock
    private TransferRepository transferRepository;

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private TransferService transferService;

    @Test
    @DisplayName("Rejects a transfer to the same account without touching repositories")
    void shouldRejectTransferToSameAccount() {

        TransferEntry transfer = new TransferEntry(1L, 1L, new BigDecimal("100.00"));

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(SameAccountTransferException.class);

        verify(transferRepository, never()).save(any(Transfer.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Throws AccountNotFoundException when the source account does not exist")
    void shouldRejectTransferWhenSourceAccountDoesNotExist() {
        TransferEntry transfer = new TransferEntry(1L, 2L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(AccountNotFoundException.class);

        verify(accountRepository, never()).existsByIdAndStatus(2L, AccountStatus.ACTIVE);
        verify(transferRepository, never()).save(any(Transfer.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Throws InactiveAccountException when the destination account is not active")
    void shouldRejectTransferWhenDestinationAccountIsInactive() {
        TransferEntry transfer = new TransferEntry(1L, 2L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.existsById(2L)).thenReturn(true);

        when(accountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(true);
        when(accountRepository.existsByIdAndStatus(2L, AccountStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessage("Destination account is inactive or blocked.");

        verify(transferRepository, never()).save(any(Transfer.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Throws InsufficientBalanceException and saves nothing when the balance is lower than the amount")
    void shouldRejectTransferWhenBalanceIsInsufficient() {
        TransferEntry transfer = new TransferEntry(1L, 2L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.existsById(2L)).thenReturn(true);

        when(accountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(true);
        when(accountRepository.existsByIdAndStatus(2L, AccountStatus.ACTIVE)).thenReturn(true);
        when(ledgerEntryRepository.calculateBalanceByAccountId(1L)).thenReturn(new BigDecimal("50.00"));

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(transferRepository, never()).save(any(Transfer.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Creates a transfer with one debit on the source and one credit on the destination")
    void shouldCreateTransferWithDebitAndCreditEntries() {
        TransferEntry transfer = new TransferEntry(1L, 2L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.existsById(2L)).thenReturn(true);

        when(accountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(true);
        when(accountRepository.existsByIdAndStatus(2L, AccountStatus.ACTIVE)).thenReturn(true);
        when(ledgerEntryRepository.calculateBalanceByAccountId(1L)).thenReturn(new BigDecimal("500.00"));

        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = transferService.createTransfer(transfer);

        assertThat(result.getFromAccountId()).isEqualTo(1L);
        assertThat(result.getToAccountId()).isEqualTo(2L);
        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        verify(transferRepository).save(any(Transfer.class));

        var entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        var entries = entryCaptor.getAllValues();
        var debit = entries.get(0);
        var credit = entries.get(1);

        assertThat(debit.getDirection()).isEqualTo(LedgerEntryDirection.DEBIT);
        assertThat(debit.getAccountId()).isEqualTo(1L);
        assertThat(debit.getAmount()).isEqualByComparingTo("100.00");

        assertThat(credit.getDirection()).isEqualTo(LedgerEntryDirection.CREDIT);
        assertThat(credit.getAccountId()).isEqualTo(2L);
        assertThat(credit.getAmount()).isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Throws AccountNotFoundException when the destination account does not exist")
    void shouldRejectTransferWhenDestinationAccountDoesNotExist() {
        TransferEntry transfer = new TransferEntry(1L, 999999L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.existsById(999999L)).thenReturn(false);

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(AccountNotFoundException.class);

        verify(transferRepository, never()).save(any(Transfer.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Throws InactiveAccountException when the source account is not active")
    void shouldRejectTransferWhenSourceAccountIsNotActive() {
        TransferEntry transfer = new TransferEntry(1L, 2L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.existsById(2L)).thenReturn(true);

        when(accountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(false);

        assertThatThrownBy(() -> transferService.createTransfer(transfer))
                .isInstanceOf(InactiveAccountException.class)
                .hasMessage("Source account is inactive or blocked.");

        verify(transferRepository, never()).save(any(Transfer.class));
        verify(ledgerEntryRepository, never()).save(any(LedgerEntry.class));
    }

    @Test
    @DisplayName("Creates the transfer when the balance is exactly the amount")
    void shouldAcceptTransferWhenBalanceEqualsAmount() {
        TransferEntry transfer = new TransferEntry(1L, 2L, new BigDecimal("100.00"));

        when(accountRepository.existsById(1L)).thenReturn(true);
        when(accountRepository.existsById(2L)).thenReturn(true);

        when(accountRepository.existsByIdAndStatus(1L, AccountStatus.ACTIVE)).thenReturn(true);
        when(accountRepository.existsByIdAndStatus(2L, AccountStatus.ACTIVE)).thenReturn(true);

        when(ledgerEntryRepository.calculateBalanceByAccountId(1L)).thenReturn(new BigDecimal("100.00"));

        when(transferRepository.save(any(Transfer.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var result = transferService.createTransfer(transfer);

        assertThat(result.getFromAccountId()).isEqualTo(1L);
        assertThat(result.getToAccountId()).isEqualTo(2L);
        assertThat(result.getAmount()).isEqualByComparingTo("100.00");
        assertThat(result.getStatus()).isEqualTo(TransferStatus.COMPLETED);

        verify(transferRepository).save(any(Transfer.class));

        var entryCaptor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository, times(2)).save(entryCaptor.capture());

        var entries = entryCaptor.getAllValues();
        var debit = entries.get(0);
        var credit = entries.get(1);

        assertThat(debit.getDirection()).isEqualTo(LedgerEntryDirection.DEBIT);
        assertThat(debit.getAccountId()).isEqualTo(1L);
        assertThat(debit.getAmount()).isEqualByComparingTo("100.00");

        assertThat(credit.getDirection()).isEqualTo(LedgerEntryDirection.CREDIT);
        assertThat(credit.getAccountId()).isEqualTo(2L);
        assertThat(credit.getAmount()).isEqualByComparingTo("100.00");
    }
}
