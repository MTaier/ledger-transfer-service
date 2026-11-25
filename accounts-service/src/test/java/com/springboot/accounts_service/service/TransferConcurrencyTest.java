package com.springboot.accounts_service.service;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountType;
import com.springboot.accounts_service.model.transfer.dto.DepositEntry;
import com.springboot.accounts_service.model.transfer.dto.TransferEntry;
import com.springboot.accounts_service.model.transfer.exception.InsufficientBalanceException;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class TransferConcurrencyTest {

    private static final int TRANSFERS = 50;

    @Autowired
    private TransferService transferService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @AfterEach
    void cleanUp() {
        ledgerEntryRepository.deleteAll();
        transferRepository.deleteAll();
        accountRepository.deleteByAccountType(AccountType.CUSTOMER);
    }

    @Test
    @DisplayName("50 parallel transfers of 10.00 from an account with 100.00: exactly 10 succeed and the balance never goes negative")
    void shouldNeverOverdraftWithParallelTransfers() throws InterruptedException {
        var source = accountRepository.save(new Account("John Doe", "88461276094"));
        var destination = accountRepository.save(new Account("Jane Doe", "12345678909"));

        transferService.createDeposit(source.getId(), new DepositEntry(new BigDecimal("100.00")));

        var completed = new AtomicInteger();
        var rejected = new AtomicInteger();
        Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        // Linha de largada: todas as threads ficam prontas e esperando,
        // e sao liberadas juntas para aumentar a chance de colisao.
        var startGate = new CountDownLatch(1);
        var finishLine = new CountDownLatch(TRANSFERS);

        ExecutorService executor = Executors.newFixedThreadPool(TRANSFERS);

        for (int i = 0; i < TRANSFERS; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    transferService.createTransfer(
                            new TransferEntry(source.getId(), destination.getId(), new BigDecimal("10.00")));
                    completed.incrementAndGet();
                } catch (InsufficientBalanceException e) {
                    rejected.incrementAndGet();
                } catch (Throwable e) {
                    // Deadlock, timeout ou qualquer outro erro: guardado para a assercao,
                    // nunca engolido, senao o teste daria um resultado falso.
                    unexpectedErrors.add(e);
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startGate.countDown();

        // Tempo maximo para o teste nunca travar o build.
        boolean finished = finishLine.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).as("all transfers finished within the time limit").isTrue();
        assertThat(unexpectedErrors).as("unexpected errors").isEmpty();
        assertThat(completed.get()).as("completed transfers").isEqualTo(10);
        assertThat(rejected.get()).as("rejected transfers").isEqualTo(40);
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(source.getId()))
                .as("source balance").isEqualByComparingTo("0.00");
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(destination.getId()))
                .as("destination balance").isEqualByComparingTo("100.00");
    }

    @Test
    @DisplayName("Crossed parallel transfers (A to B and B to A) all complete without deadlock and the balances add up")
    void shouldNotDeadlockWithCrossedParallelTransfers() throws InterruptedException {
        var accountA = accountRepository.save(new Account("John Doe", "88461276094"));
        var accountB = accountRepository.save(new Account("Jane Doe", "12345678909"));

        // Saldo de sobra nas duas contas: aqui nenhuma transferencia deve ser recusada,
        // entao qualquer falha so pode vir de deadlock ou timeout.
        transferService.createDeposit(accountA.getId(), new DepositEntry(new BigDecimal("1000.00")));
        transferService.createDeposit(accountB.getId(), new DepositEntry(new BigDecimal("1000.00")));

        var completed = new AtomicInteger();
        Queue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        var startGate = new CountDownLatch(1);
        var finishLine = new CountDownLatch(TRANSFERS);

        ExecutorService executor = Executors.newFixedThreadPool(TRANSFERS);

        for (int i = 0; i < TRANSFERS; i++) {
            // Metade vai de A para B, metade de B para A, intercaladas.
            // Sem a ordem por id, uma thread trancaria A e esperaria B,
            // enquanto outra trancaria B e esperaria A: deadlock.
            var fromA = i % 2 == 0;
            var from = fromA ? accountA.getId() : accountB.getId();
            var to = fromA ? accountB.getId() : accountA.getId();

            executor.submit(() -> {
                try {
                    startGate.await();
                    transferService.createTransfer(new TransferEntry(from, to, new BigDecimal("10.00")));
                    completed.incrementAndGet();
                } catch (Throwable e) {
                    unexpectedErrors.add(e);
                } finally {
                    finishLine.countDown();
                }
            });
        }

        startGate.countDown();

        boolean finished = finishLine.await(60, TimeUnit.SECONDS);
        executor.shutdownNow();

        assertThat(finished).as("all transfers finished within the time limit").isTrue();
        assertThat(unexpectedErrors).as("unexpected errors (deadlock or timeout)").isEmpty();
        assertThat(completed.get()).as("completed transfers").isEqualTo(TRANSFERS);

        // 25 transferencias de 10,00 em cada sentido: os saldos voltam ao inicial
        // e a soma das duas contas nunca muda.
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(accountA.getId()))
                .as("balance of A").isEqualByComparingTo("1000.00");
        assertThat(ledgerEntryRepository.calculateBalanceByAccountId(accountB.getId()))
                .as("balance of B").isEqualByComparingTo("1000.00");
    }
}
