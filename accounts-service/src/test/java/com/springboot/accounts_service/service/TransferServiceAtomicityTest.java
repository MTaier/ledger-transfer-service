package com.springboot.accounts_service.service;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import com.springboot.accounts_service.model.transfer.dto.TransferEntry;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
public class TransferServiceAtomicityTest {

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @MockitoSpyBean
    private LedgerEntryRepository ledgerEntryRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteAll();
    }

    @Test
    @DisplayName("Rolls back the transfer and the debit when saving the credit fails")
    void shouldRollbackEverythingWhenCreditEntryFails() {
        var payer = accountRepository.save(new Account("John Doe", "88461276094"));
        var receiver = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var funding = transferRepository.save(
                new Transfer(receiver.getId(), payer.getId(), new BigDecimal("500.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(
                new LedgerEntry(funding.getId(), payer.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("500.00")));

        doThrow(new RuntimeException("Simulated failure"))
                .when(ledgerEntryRepository)
                .save(argThat((LedgerEntry entry) -> entry.getDirection() == LedgerEntryDirection.CREDIT));

        var request = new TransferEntry(payer.getId(), receiver.getId(), new BigDecimal("100.00"));

        assertThatThrownBy(() -> transferService.createTransfer(request))
                .hasMessage("Simulated failure");

        assertThat(transferRepository.count()).isEqualTo(1);
        assertThat(ledgerEntryRepository.count()).isEqualTo(1);
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(payer.getId())).isEqualByComparingTo("500.00");
    }
}
