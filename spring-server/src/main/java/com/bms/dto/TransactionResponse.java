package com.bms.dto;

import com.bms.entity.BankTransaction;
import java.math.BigDecimal;
import java.time.Instant;

public record TransactionResponse(
        Long id,
        String referenceNumber,
        Long accountId,
        String accountNumber,
        String relatedAccountNumber,
        String type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        String description,
        String status,
        Instant createdAt
) {
    public static TransactionResponse from(BankTransaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getReferenceNumber(),
                transaction.getAccount().getId(),
                transaction.getAccount().getAccountNumber(),
                transaction.getRelatedAccount() == null
                        ? null : transaction.getRelatedAccount().getAccountNumber(),
                transaction.getType().name(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getDescription(),
                transaction.getStatus().name(),
                transaction.getCreatedAt());
    }
}