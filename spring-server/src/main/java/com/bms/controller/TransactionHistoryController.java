package com.bms.controller;

import com.bms.dto.TransactionHistoryResponse;
import com.bms.service.TransactionHistoryService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionHistoryController {

    private final TransactionHistoryService historyService;

    public TransactionHistoryController(TransactionHistoryService historyService) {
        this.historyService = historyService;
    }

    @GetMapping
    public TransactionHistoryResponse history(
            @AuthenticationPrincipal String username,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        return historyService.history(username, page, size);
    }
}