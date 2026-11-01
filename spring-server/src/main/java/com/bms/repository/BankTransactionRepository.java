package com.bms.repository;

import com.bms.entity.BankTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankTransactionRepository
        extends JpaRepository<BankTransaction, Long> {

    Page<BankTransaction> findAllByAccount_Owner_IdOrderByCreatedAtDesc(
            Long ownerId, Pageable pageable);
}