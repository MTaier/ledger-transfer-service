package com.springboot.accounts_service.service;

import com.springboot.accounts_service.infra.exception.AccountNotFoundException;
import com.springboot.accounts_service.infra.exception.InsufficientBalanceException;
import com.springboot.accounts_service.infra.exception.SameAccountTransferException;
import com.springboot.accounts_service.infra.exception.TransferNotFoundException;
import com.springboot.accounts_service.model.account.AccountStatus;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import com.springboot.accounts_service.model.transfer.dto.TransferEntry;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class TransferService {

    private final TransferRepository transferRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final AccountRepository accountRepository;

    public TransferService(TransferRepository transferRepository, LedgerEntryRepository ledgerEntryRepository, AccountRepository accountRepository) {
        this.transferRepository = transferRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public Transfer createTransfer(TransferEntry data) {

        if (data.toAccountId().equals(data.fromAccountId())) {
            throw new SameAccountTransferException();
        }

        if (!accountRepository.existsByIdAndStatus(data.fromAccountId(), AccountStatus.ACTIVE) ||
                !accountRepository.existsByIdAndStatus(data.toAccountId(), AccountStatus.ACTIVE)) {
            throw new AccountNotFoundException();
        }

        if (ledgerEntryRepository.calculateBalanceByAccountId(data.fromAccountId()).compareTo(data.amount()) < 0) {
            throw new InsufficientBalanceException();
        }

        var transfer = transferRepository.save(new Transfer(data.fromAccountId(), data.toAccountId(), data.amount(), TransferStatus.COMPLETED));

        ledgerEntryRepository.saveAndFlush(new LedgerEntry(transfer.getId(), transfer.getFromAccountId(), LedgerEntryDirection.DEBIT, transfer.getAmount()));
        ledgerEntryRepository.saveAndFlush(new LedgerEntry(transfer.getId(), transfer.getToAccountId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        return transfer;
    }

    public Transfer fetchTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(TransferNotFoundException::new);
    }
}
