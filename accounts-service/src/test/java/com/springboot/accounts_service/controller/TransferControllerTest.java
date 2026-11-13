package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountStatus;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import org.junit.jupiter.api.DisplayName;
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
    @DisplayName("POST /transfers returns 201 with Location header and COMPLETED status")
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
    @DisplayName("Transfer debits the source and credits the destination by the same amount")
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
    @DisplayName("POST /transfers returns 422 Insufficient balance when the source has no funds")
    void shouldRejectTransferWhenBalanceIsInsufficient() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        mockMvc.perform(post("/transfers")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Insufficient balance"))
                .andExpect(jsonPath("$.detail").value("Insufficient funds to complete this transfer."));
    }

    @Test
    @DisplayName("POST /transfers returns 422 Invalid transfer when source and destination are the same")
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
                .andExpect(jsonPath("$.title").value("Invalid transfer"))
                .andExpect(jsonPath("$.detail").value("Source and destination accounts must be different."));
    }

    @Test
    @DisplayName("POST /transfers returns 404 Account not found when the destination does not exist")
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
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Destination account not found."));
    }

    @Test
    @DisplayName("POST /transfers returns 400 Invalid data when the amount has more than 2 decimals")
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
    @DisplayName("GET /transfers/{id} returns 200 with all transfer fields")
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
    @DisplayName("GET /transfers/{id} returns 404 ProblemDetail when the transfer does not exist")
    void shouldReturnNotFoundWhenTransferDoesNotExist() throws Exception {
        mockMvc.perform(get("/transfers/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Transfer not found"));
    }

    @Test
    @DisplayName("POST /transfers returns 422 Account not active when the source is INACTIVE")
    void shouldRejectTransferFromInactiveAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        source.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(source);

        mockMvc.perform(post("/transfers")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Account not active"))
                .andExpect(jsonPath("$.detail").value("Source account is inactive or blocked."));
    }

    @Test
    @DisplayName("POST /transfers returns 422 Account not active when the source is BLOCKED")
    void shouldRejectTransferFromBlockedAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        source.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(source);

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Account not active"))
                .andExpect(jsonPath("$.detail").value("Source account is inactive or blocked."));
    }

    @Test
    @DisplayName("POST /transfers returns 422 Account not active when the destination is INACTIVE")
    void shouldRejectTransferToInactiveAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        account.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(account);

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Account not active"))
                .andExpect(jsonPath("$.detail").value("Destination account is inactive or blocked."));
    }

    @Test
    @DisplayName("POST /transfers returns 422 Account not active when the destination is BLOCKED")
    void shouldRejectTransferToBlockedAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        account.setStatus(AccountStatus.BLOCKED);
        accountRepository.save(account);

        mockMvc.perform(post("/transfers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"fromAccountId": %d, "toAccountId": %d, "amount": 50.00}
                            """.formatted(source.getId(), account.getId())))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Account not active"))
                .andExpect(jsonPath("$.detail").value("Destination account is inactive or blocked."));
    }
}
