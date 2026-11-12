package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfiguration.class)
public class LedgerEntryRepositoryTest {

    private static final String VALID_DOCUMENT_1 = "12345678909";
    private static final String VALID_DOCUMENT_2 = "88461276094";

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    void shouldReturnZeroWhenAccountHasNoEntries() {
        var account = accountRepository.save(new Account("John Doe", VALID_DOCUMENT_1));

        var balance = ledgerEntryRepository.calculateBalanceByAccountId(account.getId());

        assertThat(balance).isZero();
    }

    @Test
    void shouldSumCreditsWhenAccountHasOnlyCredits() {
        var account = accountRepository.save(new Account("John Doe", VALID_DOCUMENT_1));
        var source = accountRepository.save(new Account("Jane Doe", VALID_DOCUMENT_2));

        var firstTransfer = transferRepository.save(
                new Transfer(source.getId(), account.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        var secondTransfer = transferRepository.save(
                new Transfer(source.getId(), account.getId(), new BigDecimal("50.00"), TransferStatus.COMPLETED));

        ledgerEntryRepository.save(new LedgerEntry(firstTransfer.getId(), account.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("100.00")));
        ledgerEntryRepository.save(new LedgerEntry(secondTransfer.getId(), account.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("50.00")));

        var balance = ledgerEntryRepository.calculateBalanceByAccountId(account.getId());

        assertThat(balance).isEqualByComparingTo("150.00");
    }

    @Test
    void shouldSubtractDebitsFromCredits() {
        var account = accountRepository.save(new Account("John Doe", VALID_DOCUMENT_1));
        var source = accountRepository.save(new Account("Jane Doe", VALID_DOCUMENT_2));

        var firstTransfer = transferRepository.save(
                new Transfer(source.getId(), account.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        var secondTransfer = transferRepository.save(
                new Transfer(account.getId(), source.getId(), new BigDecimal("50.00"), TransferStatus.COMPLETED));

        ledgerEntryRepository.save(new LedgerEntry(firstTransfer.getId(), account.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("100.00")));
        ledgerEntryRepository.save(new LedgerEntry(secondTransfer.getId(), account.getId(), LedgerEntryDirection.DEBIT, new BigDecimal("50.00")));

        var balance = ledgerEntryRepository.calculateBalanceByAccountId(account.getId());

        assertThat(balance).isEqualByComparingTo("50.00");
    }

    @Test
    void shouldIgnoreEntriesFromOtherAccounts() {
        var account1 = accountRepository.save(new Account("John Doe", VALID_DOCUMENT_1));
        var account2 = accountRepository.save(new Account("Jane Doe", VALID_DOCUMENT_2));
        var source = accountRepository.save(new Account("Maria Silva", "27879719032"));

        var firstTransfer = transferRepository.save(
                new Transfer(source.getId(), account1.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        var secondTransfer = transferRepository.save(
                new Transfer(source.getId(), account2.getId(), new BigDecimal("500.00"), TransferStatus.COMPLETED));

        ledgerEntryRepository.save(new LedgerEntry(firstTransfer.getId(), account1.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("100.00")));
        ledgerEntryRepository.save(new LedgerEntry(secondTransfer.getId(), account2.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("500.00")));

        var balanceAccount2 = ledgerEntryRepository.calculateBalanceByAccountId(account2.getId());

        assertThat(balanceAccount2).isEqualByComparingTo("500.00");
    }
}
