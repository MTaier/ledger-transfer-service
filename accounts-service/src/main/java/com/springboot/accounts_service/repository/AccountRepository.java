package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.model.account.Account;
import com.springboot.accounts_service.model.account.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByDocument(String document);

    boolean existsByIdAndStatus(Long id, AccountStatus status);
}
