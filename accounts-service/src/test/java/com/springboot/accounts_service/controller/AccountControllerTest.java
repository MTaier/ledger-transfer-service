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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@Transactional
public class AccountControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TransferRepository transferRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Test
    @DisplayName("POST /accounts returns 201 with Location header and masked CPF")
    void shouldCreateAccount() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerName": "John Doe", "document": "884.612.760-94"}
                            """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.document").value("***.612.760-**"));
    }

    @Test
    @DisplayName("POST /accounts returns 400 Invalid CPF when check digits are wrong")
    void shouldRejectInvalidCpf() throws Exception {
        mockMvc.perform(post("/accounts")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                            {"ownerName": "John Doe", "document": "884.612.760-95"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid CPF"));
    }

    @Test
    @DisplayName("POST /accounts returns 400 Invalid data with field errors when a field is blank")
    void shouldRejectBlankFields() throws Exception {
        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"ownerName": "", "document": "884.612.760-94"}
                            """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ownerName").exists())
                .andExpect(jsonPath("$.title").value("Invalid data"));

    }

    @Test
    @DisplayName("POST /accounts returns 409 when the CPF is already registered")
    void shouldRejectDuplicateCpf() throws Exception {
        var body = """
            {"ownerName": "John Doe", "document": "884.612.760-94"}
            """;

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.title").value("CPF already registered"));
    }

    @Test
    @DisplayName("GET /accounts/{id} returns 404 when the account does not exist")
    void shouldThrowWhenAccountDoesNotExist() throws Exception {
        mockMvc.perform(get("/accounts/999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance returns 200 with the sum of ledger entries")
    void shouldReturnAccountBalance() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var source = accountRepository.save(new Account("Jane Doe", "12345678909"));

        var accountId = account.getId();

        var firstTransfer = transferRepository.save(
                new Transfer(source.getId(), account.getId(), new BigDecimal("100.00"), TransferStatus.COMPLETED));

        ledgerEntryRepository.save(new LedgerEntry(firstTransfer.getId(), account.getId(), LedgerEntryDirection.CREDIT, new BigDecimal("100.00")));

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance returns 0.0 for an account without entries")
    void shouldReturnZeroBalanceForNewAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var accountId = account.getId();

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value(accountId))
                .andExpect(jsonPath("$.balance").value(0.0));
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance returns 404 ProblemDetail when the account does not exist")
    void shouldReturnNotFoundForBalanceOfMissingAccount() throws Exception {
        mockMvc.perform(get("/accounts/999999/balance"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.title").value("Account not found"));
    }

}
