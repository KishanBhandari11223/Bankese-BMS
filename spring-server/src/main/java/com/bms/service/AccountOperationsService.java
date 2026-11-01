package com.bms.service;

import com.bms.dto.BalanceResponse;
import com.bms.dto.MoneyRequest;
import com.bms.dto.TransactionResponse;
import com.bms.entity.Account;
import com.bms.entity.BankTransaction;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountOperationsService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final CurrentCustomerService currentCustomerService;

    public AccountOperationsService(
            AccountRepository accountRepository,
            BankTransactionRepository transactionRepository,
            CurrentCustomerService currentCustomerService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.currentCustomerService = currentCustomerService;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(String username, Long accountId) {
        User customer = currentCustomerService.requireCustomer(username);
        Account account = accountRepository.findByIdAndOwner_Id(
                        accountId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT, "Account is not active");
        }
        return BalanceResponse.from(account);
    }

    @Transactional
    public TransactionResponse deposit(
            String username, Long accountId, MoneyRequest request) {
        User customer = currentCustomerService.requireCustomer(username);
        Account account = ownedActiveAccount(accountId, customer.getId());
        BigDecimal amount = normalizedAmount(request.amount());
        BigDecimal newBalance = account.getBalance().add(amount);
        ensureFitsBalanceColumn(newBalance);
        account.credit(amount);
        BankTransaction transaction = new BankTransaction(
                UUID.randomUUID().toString(), account, null,
                BankTransaction.Type.DEPOSIT, amount, newBalance,
                request.description());
        accountRepository.save(account);
        return TransactionResponse.from(transactionRepository.saveAndFlush(transaction));
    }

    @Transactional
    public TransactionResponse withdraw(
            String username, Long accountId, MoneyRequest request) {
        User customer = currentCustomerService.requireCustomer(username);
        Account account = ownedActiveAccount(accountId, customer.getId());
        BigDecimal amount = normalizedAmount(request.amount());
        if (account.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Insufficient funds");
        }
        BigDecimal newBalance = account.getBalance().subtract(amount);
        account.debit(amount);
        BankTransaction transaction = new BankTransaction(
                UUID.randomUUID().toString(), account, null,
                BankTransaction.Type.WITHDRAWAL, amount, newBalance,
                request.description());
        accountRepository.save(account);
        return TransactionResponse.from(transactionRepository.saveAndFlush(transaction));
    }

    private Account ownedActiveAccount(Long accountId, Long ownerId) {
        Account account = accountRepository.findOwnedForUpdate(accountId, ownerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found"));
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Account is not active");
        }
        return account;
    }

    private BigDecimal normalizedAmount(BigDecimal amount) {
        try {
            return amount.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must have no more than two decimal places");
        }
    }

    private void ensureFitsBalanceColumn(BigDecimal balance) {
        if (balance.precision() > 19) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Balance exceeds the supported limit");
        }
    }
}