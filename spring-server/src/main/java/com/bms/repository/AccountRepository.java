package com.bms.repository;

import com.bms.entity.Account;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountRepository extends JpaRepository<Account, Long> {

    List<Account> findAllByOwner_IdOrderByIdDesc(Long ownerId);

    List<Account> findAllByOwner_IdAndStatusOrderByIdDesc(
            Long ownerId, Account.Status status);

    long countByOwner_Id(Long ownerId);

    boolean existsByOwner_IdAndStatus(Long ownerId, Account.Status status);

    Optional<Account> findByIdAndOwner_Id(Long id, Long ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id and a.owner.id = :ownerId")
    Optional<Account> findOwnedForUpdate(
            @Param("id") Long id, @Param("ownerId") Long ownerId);

    Optional<Account> findByAccountNumber(String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber = :accountNumber")
    Optional<Account> findByAccountNumberForUpdate(
            @Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.accountNumber in :numbers order by a.id")
    List<Account> findTransferAccountsForUpdate(
            @Param("numbers") Collection<String> accountNumbers);

    boolean existsByAccountNumber(String accountNumber);
}