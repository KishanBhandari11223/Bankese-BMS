package com.bms.service;

import com.bms.dto.TransferRequest;
import com.bms.dto.TransferResponse;
import com.bms.entity.Account;
import com.bms.entity.BankTransaction;
import com.bms.entity.User;
import com.bms.repository.AccountRepository;
import com.bms.repository.BankTransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final BankTransactionRepository transactionRepository;
    private final CurrentCustomerService currentCustomerService;

    public TransferService(
            AccountRepository accountRepository,
            BankTransactionRepository transactionRepository,
            CurrentCustomerService currentCustomerService) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.currentCustomerService = currentCustomerService;
    }

    @Transactional
    public TransferResponse transfer(
            String username, Long sourceAccountId, TransferRequest request) {
        User customer = currentCustomerService.requireCustomer(username);
        Account sourceSnapshot = accountRepository.findByIdAndOwner_Id(
                        sourceAccountId, customer.getId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Source account not found"));
        String destinationNumber = request.destinationAccountNumber().trim();
        String sourceNumber = sourceSnapshot.getAccountNumber();
        if (sourceNumber.equals(destinationNumber)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot transfer to the same account");
        }

        List<Account> lockedAccounts = accountRepository
                .findTransferAccountsForUpdate(List.of(sourceNumber, destinationNumber));
        Account source = lockedAccounts.stream()
                .filter(account -> account.getAccountNumber().equals(sourceNumber))
                .findFirst()
                .filter(account -> account.getOwner().getId().equals(customer.getId()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Source account not found"));
        Account destination = lockedAccounts.stream()
                .filter(account -> account.getAccountNumber().equals(destinationNumber))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Destination account not found"));

        requireActive(source);
        requireActive(destination);
        if (!source.getCurrency().equals(destination.getCurrency())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Accounts must use the same currency");
        }

        BigDecimal amount = normalizedAmount(request.amount());
        if (source.getBalance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Insufficient funds");
        }
        BigDecimal sourceBalance = source.getBalance().subtract(amount);
        BigDecimal destinationBalance = destination.getBalance().add(amount);
        if (destinationBalance.precision() > 19) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Destination balance exceeds the supported limit");
        }

        source.debit(amount);
        destination.credit(amount);
        String referenceNumber = UUID.randomUUID().toString();
        String description = request.description();
        BankTransaction debit = new BankTransaction(
                referenceNumber, source, destination,
                BankTransaction.Type.TRANSFER_OUT, amount, sourceBalance, description);
        BankTransaction credit = new BankTransaction(
                referenceNumber, destination, source,
                BankTransaction.Type.TRANSFER_IN, amount, destinationBalance, description);

        accountRepository.saveAll(List.of(source, destination));
        transactionRepository.saveAll(List.of(debit, credit));
        transactionRepository.flush();

        return new TransferResponse(
                referenceNumber, sourceNumber, destinationNumber, amount,
                sourceBalance, destinationBalance, "COMPLETED");
    }

    private void requireActive(Account account) {
        if (account.getStatus() != Account.Status.ACTIVE) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT, "Account is not active");
        }
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
}