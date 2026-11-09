package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.model.Account;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    boolean existsByDocument(String document);
}
