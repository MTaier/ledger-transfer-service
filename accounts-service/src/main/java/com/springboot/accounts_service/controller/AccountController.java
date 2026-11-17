package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.model.account.dto.DataAccountBalance;
import com.springboot.accounts_service.model.account.dto.DataAccountCreation;
import com.springboot.accounts_service.model.account.dto.DataAccountDetails;
import com.springboot.accounts_service.model.transfer.dto.DepositEntry;
import com.springboot.accounts_service.model.transfer.dto.TransferExit;
import com.springboot.accounts_service.service.AccountService;
import com.springboot.accounts_service.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final AccountService accountService;
    private final TransferService transferService;

    public AccountController(AccountService accountService, TransferService transferService) {
        this.accountService = accountService;
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<DataAccountDetails> postAccount(@RequestBody @Valid DataAccountCreation data, UriComponentsBuilder uriBuilder) {

        var account = accountService.createAccount(data);

        var uri = uriBuilder
                .path("/accounts/{id}")
                .buildAndExpand(account.getId())
                .toUri();

        return ResponseEntity.created(uri)
                .body(new DataAccountDetails(account));

    }

    @GetMapping
    public ResponseEntity<PagedModel<DataAccountDetails>> getAllAccounts(
            @PageableDefault(size = 10) Pageable pageable) {

        var accounts = accountService.listAllAccounts(pageable);

        var response = accounts.map(DataAccountDetails::new);

        return ResponseEntity.ok(new PagedModel<>(response));

    }

    @GetMapping("/{id}")
    public ResponseEntity<DataAccountDetails> getAccount(@PathVariable Long id) {

        var account = accountService.fetchAccount(id);

        return ResponseEntity.ok(new DataAccountDetails(account));

    }

    @GetMapping("/{id}/balance")
    public ResponseEntity<DataAccountBalance> getAccountBalance(@PathVariable Long id) {

        var balance = accountService.getBalance(id);

        return ResponseEntity.ok(new DataAccountBalance(id, balance));

    }

    @PostMapping("/{id}/deposits")
    public ResponseEntity<TransferExit> postDeposit(@PathVariable Long id, @RequestBody @Valid DepositEntry depositData, UriComponentsBuilder uriBuilder) {

        var deposit = transferService.createDeposit(id, depositData);

        var uri = uriBuilder
                .path("/transfers/{id}")
                .buildAndExpand(deposit.getId())
                .toUri();

        return ResponseEntity.created(uri)
                .body(new TransferExit(deposit));

    }

}
