package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public class TransferControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;


    @Test
    void shouldCreateTransfer() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var transfer = transferRepository.save(new Transfer(account.getId(), source.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

       mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldUpdateBalancesAfterTransfer() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var accountId = account.getId();
        var sourceId = source.getId();

        var transfer = transferRepository.save(new Transfer(account.getId(), source.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));

        mockMvc.perform(get("/accounts/{sourceId}/balance", sourceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(50.00));
    }

    @Test
    void shouldRejectTransferWhenBalanceIsInsufficient() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        mockMvc.perform(post("/transfers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Insufficient balance"));
    }

    @Test
    void shouldRejectTransferToSameAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var transfer = transferRepository.save(new Transfer(source.getId(), account.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), account.getId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(account.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Invalid transfer"));
    }

    @Test
    void shouldRejectTransferWhenAccountDoesNotExist() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var transfer = transferRepository.save(new Transfer(source.getId(), account.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), account.getId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"fromAccountId": %d, "toAccountId": 999999, "amount": 50.00}
                            """.formatted(account.getId())))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

    @Test
    void shouldRejectInvalidTransferBody() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var transfer = transferRepository.save(new Transfer(account.getId(), source.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.000}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.amount").exists())
                .andExpect(jsonPath("$.title").value("Invalid data"));
    }

    @Test
    void shouldReturnTransferById() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var transfer = transferRepository.save(new Transfer(account.getId(), source.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), source.getId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        var transferId = transfer.getId();

        mockMvc.perform(get("/transfers/{transferId}", transferId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transferId))
                .andExpect(jsonPath("$.fromAccountId").value(account.getId()))
                .andExpect(jsonPath("$.toAccountId").value(source.getId()))
                .andExpect(jsonPath("$.amount").value(100.00))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    void shouldReturnNotFoundWhenTransferDoesNotExist() throws Exception {
        mockMvc.perform(get("/transfers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transfer not found"));
    }
}
