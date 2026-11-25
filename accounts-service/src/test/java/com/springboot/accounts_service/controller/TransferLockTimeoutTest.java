package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountType;
import com.springboot.accounts_service.model.transfer.dto.DepositEntry;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import com.springboot.accounts_service.service.TransferService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Uma segunda thread segura a trava da conta de origem, e a transferencia
 * pela API desiste depois do tempo maximo de espera.
 * O tempo cai para 1 segundo so nesta classe, para o teste ser rapido.
 */
@SpringBootTest(properties = "spring.datasource.hikari.connection-init-sql=SET SESSION innodb_lock_wait_timeout=1")
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class TransferLockTimeoutTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteByAccountType(AccountType.CUSTOMER);
    }

    @Test
    @DisplayName("POST /transfers returns 409 when the account stays locked longer than the lock wait timeout")
    void shouldReturnConflictWhenLockWaitTimesOut() throws Exception {
        var source = accountRepository.save(new Account("John Doe", "88461276094"));
        var destination = accountRepository.save(new Account("Jane Doe", "12345678909"));
        transferService.createDeposit(source.getId(), new DepositEntry(new BigDecimal("100.00")));

        // lockAcquired: avisa que a trava ja existe, para a requisicao nao chegar antes dela.
        // release: segura a transacao aberta ate a requisicao terminar.
        var lockAcquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();

        var holder = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
            accountRepository.findCustomerByIdForUpdate(source.getId());
            lockAcquired.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));

        try {
            assertThat(lockAcquired.await(10, TimeUnit.SECONDS)).as("lock acquired by the holder").isTrue();

            mockMvc.perform(post("/transfers")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                                """.formatted(source.getId(), destination.getId())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.title").value("Account temporarily unavailable"))
                    .andExpect(jsonPath("$.detail").value("The account is temporarily unavailable. Please try again later."));
        } finally {
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            executor.shutdownNow();
        }

        // A transferencia que desistiu nao deixou nada gravado.
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(source.getId()))
                .as("source balance").isEqualByComparingTo("100.00");
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(destination.getId()))
                .as("destination balance").isEqualByComparingTo("0.00");
    }
}
