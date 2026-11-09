package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.model.dto.DataAccountCreation;
import com.springboot.accounts_service.model.dto.DataAccountDetails;
import com.springboot.accounts_service.service.AccountService;
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

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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
}
