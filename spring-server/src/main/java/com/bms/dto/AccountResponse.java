package com.bms.dto;

import com.bms.entity.Account;

public record AccountResponse(
        Long id,
        String accountNumber,
        String accountType,
        String currency,
        String status
) {
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getAccountNumber(),
                account.getAccountType().name(),
                account.getCurrency(),
                account.getStatus().name());
    }
}