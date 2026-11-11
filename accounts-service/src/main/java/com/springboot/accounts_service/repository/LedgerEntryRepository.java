package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.model.ledger.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {

    @Query(value = """
        SELECT COALESCE(
                    SUM(
                        CASE
                            WHEN direction = 'CREDIT' THEN amount
                            WHEN direction = 'DEBIT' THEN -amount
                            ELSE 0
                        END
                    ),
                    0.00
                )
                FROM ledger_entries
                WHERE account_id = :accountId
    """, nativeQuery = true)
    BigDecimal calculateBalanceByAccountId(Long accountId);

}
