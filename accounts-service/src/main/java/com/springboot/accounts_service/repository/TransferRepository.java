package com.springboot.accounts_service.repository;

import com.springboot.accounts_service.model.transfer.Transfer;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransferRepository extends JpaRepository<Transfer, Long> {


}
