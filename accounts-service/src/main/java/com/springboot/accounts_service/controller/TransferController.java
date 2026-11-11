package com.springboot.accounts_service.controller;

import com.springboot.accounts_service.model.account.dto.DataAccountDetails;
import com.springboot.accounts_service.model.transfer.dto.TransferEntry;
import com.springboot.accounts_service.model.transfer.dto.TransferExit;
import com.springboot.accounts_service.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public ResponseEntity<TransferExit> postTransfer(@RequestBody @Valid TransferEntry data, UriComponentsBuilder uriBuilder) {

        var transfer = transferService.createTransfer(data);

        var uri = uriBuilder
                .path("/transfers/{id}")
                .buildAndExpand(transfer.getId())
                .toUri();

        return ResponseEntity.created(uri)
                .body(new TransferExit(transfer));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransferExit> getTransfer(@PathVariable Long id) {

        var transfer = transferService.fetchTransfer(id);

        return ResponseEntity.ok(new TransferExit(transfer));

    }


}
