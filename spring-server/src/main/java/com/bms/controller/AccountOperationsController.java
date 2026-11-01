package com.bms.controller;

import com.bms.dto.BalanceResponse;
import com.bms.dto.MoneyRequest;
import com.bms.dto.TransactionResponse;
import com.bms.dto.VerifyPasswordRequest;
import com.bms.service.AccountOperationsService;
import com.bms.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}")
public class AccountOperationsController {

    private final AccountOperationsService operationsService;
    private final AuthService authService;

    public AccountOperationsController(
            AccountOperationsService operationsService,
            AuthService authService) {
        this.operationsService = operationsService;
        this.authService = authService;
    }

    @PostMapping("/balance")
    public BalanceResponse balance(
            @AuthenticationPrincipal String username,
            @PathVariable Long accountId,
            @Valid @RequestBody VerifyPasswordRequest request) {
        authService.verifyCurrentPassword(username, request);
        return operationsService.balance(username, accountId);
    }

    @PostMapping("/deposit")
    public TransactionResponse deposit(
            @AuthenticationPrincipal String username,
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest request) {
        return operationsService.deposit(username, accountId, request);
    }

    @PostMapping("/withdraw")
    public TransactionResponse withdraw(
            @AuthenticationPrincipal String username,
            @PathVariable Long accountId,
            @Valid @RequestBody MoneyRequest request) {
        return operationsService.withdraw(username, accountId, request);
    }
}