package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountStatus;
import com.springboot.accounts_service.model.account.AccountType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByDocument(String document);

    boolean existsByIdAndStatus(Long id, AccountStatus status);

    boolean existsByIdAndAccountType(Long id, AccountType accountType);

    Optional<Account> findByAccountType(AccountType accountType);

    @Transactional
    void deleteByAccountType(AccountType accountType);

    Page<Account> findAllByAccountType(AccountType accountType, Pageable pageable);

    Optional<Account> findByIdAndAccountType(Long id, AccountType accountType);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
        SELECT a
        FROM Account a
        WHERE a.id = :id
            AND a.accountType = com.springboot.accounts_service.model.account.AccountType.CUSTOMER
    """)
    Optional<Account> findCustomerByIdForUpdate(Long id);
}
