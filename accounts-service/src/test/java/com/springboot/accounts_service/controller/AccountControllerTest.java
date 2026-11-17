package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.TestcontainersConfiguration;
import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountStatus;
import com.springboot.accounts_service.model.account.AccountType;
import com.springboot.accounts_service.repository.AccountRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;


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
        var accountId = account.getId();

        deposit(accountId, "100.00");

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

    @Test
    @DisplayName("POST /accounts/{id}/deposits returns 201 and increases the balance by exactly the amount")
    void shouldDepositAndIncreaseBalance() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var accountId = account.getId();

        mockMvc.perform(post("/accounts/{accountId}/deposits", accountId)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                                {"amount": 100.00}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.toAccountId").value(accountId))
                .andExpect(jsonPath("$.amount").value(100.0));

        mockMvc.perform(get("/accounts/{accountId}/balance", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(100.0));
    }

    @Test
    @DisplayName("POST /accounts/{id}/deposits returns 404 Account not found when the account does not exist")
    void shouldRejectDepositToMissingAccount() throws Exception {
        mockMvc.perform(post("/accounts/999999/deposits")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"amount": 100.00}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Account not found."));
    }

    @Test
    @DisplayName("POST /accounts/{id}/deposits returns 422 Account not active when the account is INACTIVE")
    void shouldRejectDepositToInactiveAccount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        account.setStatus(AccountStatus.INACTIVE);
        accountRepository.save(account);
        var accountId = account.getId();

        mockMvc.perform(post("/accounts/{accountId}/deposits", accountId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"amount": 100.00}
                                """))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Account not active"))
                .andExpect(jsonPath("$.detail").value("Account not active."));
    }

    @Test
    @DisplayName("POST /accounts/{id}/deposits returns 404 Account not found for the cash account")
    void shouldRejectDepositToCashAccount() throws Exception {
        var cashAccount = accountRepository.findByAccountType(AccountType.CASH)
                .orElseThrow();

        mockMvc.perform(post("/accounts/{accountId}/deposits", cashAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount": 100.00}
                            """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Account not found."));
    }

    @Test
    @DisplayName("POST /accounts/{id}/deposits returns 400 Invalid data when the amount is negative")
    void shouldRejectDepositWithInvalidAmount() throws Exception {
        var account = accountRepository.save(new Account("John Doe", "88461276094"));
        var accountId = account.getId();

        mockMvc.perform(post("/accounts/{accountId}/deposits", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"amount": -100.00}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid data"))
                .andExpect(jsonPath("$.detail").value("Invalid request content."));
    }

    @Test
    @DisplayName("GET /accounts does not list the cash account")
    void shouldNotListCashAccount() throws Exception {
        var cashAccount = accountRepository.findByAccountType(AccountType.CASH)
                .orElseThrow();

        var customer = accountRepository.save(new Account("John Doe", "88461276094"));

        mockMvc.perform(get("/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(customer.getId()))
                .andExpect(jsonPath(
                        "$.content[?(@.id == " + cashAccount.getId() + ")]"
                ).isEmpty());
    }

    @Test
    @DisplayName("GET /accounts/{id} returns 404 Account not found for the cash account")
    void shouldReturnNotFoundForCashAccount() throws Exception {
        var cashAccount = accountRepository.findByAccountType(AccountType.CASH)
                .orElseThrow();

        mockMvc.perform(get("/accounts/{cashAccountId}", cashAccount.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Account not found."));
    }

    @Test
    @DisplayName("GET /accounts/{id}/balance returns 404 Account not found for the cash account")
    void shouldReturnNotFoundForCashAccountBalance() throws Exception {
        var cashAccount = accountRepository.findByAccountType(AccountType.CASH)
                .orElseThrow();

        mockMvc.perform(get("/accounts/{cashAccountId}/balance", cashAccount.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Account not found"))
                .andExpect(jsonPath("$.detail").value("Account not found."));
    }

    private void deposit(Long accountId, String amount) throws Exception {
        mockMvc.perform(post("/accounts/{accountId}/deposits", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                            {"amount": %s}
                            """.formatted(amount)))
                .andExpect(status().isCreated());
    }
}
