package com.bms.service;

import com.bms.dto.AccountResponse;
import com.bms.dto.CreateAccountRequest;
import com.bms.entity.Account;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AccountService {

    private final AccountRepository accountRepository;
    private final CurrentCustomerService currentCustomerService;

    public AccountService(
            AccountRepository accountRepository,
            CurrentCustomerService currentCustomerService) {
        this.accountRepository = accountRepository;
        this.currentCustomerService = currentCustomerService;
    }

    @Transactional
    public AccountResponse create(String username, CreateAccountRequest request) {
        User customer = currentCustomerService.requireCustomerForUpdate(username);
        if (accountRepository.existsByOwner_IdAndStatus(
            customer.getId(), Account.Status.PENDING_APPROVAL)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Wait for the pending account request to be reviewed");
        }
        Account account = new Account(
            customer, nextAccountNumber(), request.accountType(),
            Account.Status.PENDING_APPROVAL);
        return AccountResponse.from(accountRepository.saveAndFlush(account));
    }

        @Transactional
        public List<AccountResponse> createDefaultAccounts(User customer) {
        if (accountRepository.countByOwner_Id(customer.getId()) > 0) {
            return accountRepository.findAllByOwner_IdOrderByIdDesc(customer.getId())
                .stream().map(AccountResponse::from).toList();
        }
        List<Account> defaults = List.of(
            new Account(customer, nextAccountNumber(), Account.Type.SAVINGS),
            new Account(customer, nextAccountNumber(), Account.Type.CURRENT));
        return accountRepository.saveAllAndFlush(defaults).stream()
            .map(AccountResponse::from).toList();
        }

        @Transactional
        public AccountResponse updateAccountStatus(Long customerId, Long accountId,
            Account.Status status) {
        Account account = accountRepository.findByIdAndOwner_Id(accountId, customerId)
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "Account not found"));
        account.setStatus(status);
        return AccountResponse.from(accountRepository.saveAndFlush(account));
        }

    @Transactional(readOnly = true)
    public List<AccountResponse> list(String username) {
        User customer = currentCustomerService.requireCustomer(username);
        return accountRepository.findAllByOwner_IdOrderByIdDesc(customer.getId())
                .stream().map(AccountResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public AccountResponse get(String username, Long accountId) {
        User customer = currentCustomerService.requireCustomer(username);
        return AccountResponse.from(accountRepository
                .findByIdAndOwner_Id(accountId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Account not found")));
    }

    private String nextAccountNumber() {
        String accountNumber;
        do {
            accountNumber = Long.toString(ThreadLocalRandom.current()
                    .nextLong(1_000_000_000_000_000L, 9_999_999_999_999_999L));
        } while (accountRepository.existsByAccountNumber(accountNumber));
        return accountNumber;
    }
}