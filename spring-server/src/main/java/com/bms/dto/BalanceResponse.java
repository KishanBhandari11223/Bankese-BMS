package com.bms.dto;

import com.bms.entity.Account;
import java.math.BigDecimal;

public record BalanceResponse(
        Long accountId,
        String accountNumber,
        BigDecimal balance,
        String currency
) {
    public static BalanceResponse from(Account account) {
        return new BalanceResponse(
                account.getId(), account.getAccountNumber(),
                account.getBalance(), account.getCurrency());
    }
}