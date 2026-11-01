package com.bms.controller;

import com.bms.dto.AccountResponse;
import com.bms.dto.CreateAccountRequest;
import com.bms.service.AccountService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(
            @AuthenticationPrincipal String username,
            @Valid @RequestBody CreateAccountRequest request) {
        return accountService.create(username, request);
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal String username) {
        return accountService.list(username);
    }

    @GetMapping("/{accountId}")
    public AccountResponse get(
            @AuthenticationPrincipal String username,
            @PathVariable Long accountId) {
        return accountService.get(username, accountId);
    }
}