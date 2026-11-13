package com.springboot.accounts_service.service;

import com.springboot.accounts_service.model.account.exception.AccountNotFoundException;
import com.springboot.accounts_service.model.account.exception.InactiveAccountException;
import com.springboot.accounts_service.model.transfer.exception.InsufficientBalanceException;
import com.springboot.accounts_service.model.transfer.exception.SameAccountTransferException;
import com.springboot.accounts_service.model.transfer.exception.TransferNotFoundException;
import com.springboot.accounts_service.model.account.AccountStatus;
import com.springboot.accounts_service.model.ledger.LedgerEntry;
import com.springboot.accounts_service.model.ledger.LedgerEntryDirection;
import com.springboot.accounts_service.model.transfer.Transfer;
import com.springboot.accounts_service.model.transfer.TransferStatus;
import com.springboot.accounts_service.model.transfer.dto.TransferEntry;
import com.springboot.accounts_service.repository.AccountRepository;
import com.springboot.accounts_service.repository.LedgerEntryRepository;
import com.springboot.accounts_service.repository.TransferRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

        if (!accountRepository.existsById(data.fromAccountId())) {
            throw new AccountNotFoundException("Source account not found.");
        }

        if (!accountRepository.existsById(data.toAccountId())) {
            throw new AccountNotFoundException("Destination account not found.");
        }

        if (!accountRepository.existsByIdAndStatus(data.fromAccountId(), AccountStatus.ACTIVE)) {
            throw new InactiveAccountException("Source account is inactive or blocked.");
        }

        if (!accountRepository.existsByIdAndStatus(data.toAccountId(), AccountStatus.ACTIVE)) {
            throw new InactiveAccountException("Destination account is inactive or blocked.");
        }

        if (ledgerEntryRepository.calculateBalanceByAccountId(data.fromAccountId()).compareTo(data.amount()) < 0) {
            throw new InsufficientBalanceException();
        }

        var transfer = transferRepository.save(new Transfer(data.fromAccountId(), data.toAccountId(), data.amount(), TransferStatus.COMPLETED));

        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), transfer.getFromAccountId(), LedgerEntryDirection.DEBIT, transfer.getAmount()));
        ledgerEntryRepository.save(new LedgerEntry(transfer.getId(), transfer.getToAccountId(), LedgerEntryDirection.CREDIT, transfer.getAmount()));

        return transfer;
    }

    public Transfer fetchTransfer(Long id) {
        return transferRepository.findById(id)
                .orElseThrow(TransferNotFoundException::new);
    }
}
