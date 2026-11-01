package com.bms.service;

import com.bms.dto.TransactionHistoryResponse;
import com.bms.dto.TransactionResponse;
import com.bms.entity.User;
import com.bms.repository.BankTransactionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class TransactionHistoryService {

    private final BankTransactionRepository transactionRepository;
    private final CurrentCustomerService currentCustomerService;

    public TransactionHistoryService(
            BankTransactionRepository transactionRepository,
            CurrentCustomerService currentCustomerService) {
        this.transactionRepository = transactionRepository;
        this.currentCustomerService = currentCustomerService;
    }

    @Transactional(readOnly = true)
    public TransactionHistoryResponse history(String username, int page, int size) {
        User customer = currentCustomerService.requireCustomer(username);
        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Page must be nonnegative and size must be 1-100");
        }
        Page<TransactionResponse> transactions = transactionRepository
                .findAllByAccount_Owner_IdOrderByCreatedAtDesc(
                        customer.getId(), PageRequest.of(page, size))
                .map(TransactionResponse::from);
        return new TransactionHistoryResponse(
                transactions.getContent(),
                transactions.getNumber(),
                transactions.getSize(),
                transactions.getTotalElements(),
                transactions.getTotalPages());
    }
}