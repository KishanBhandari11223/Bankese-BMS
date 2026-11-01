package com.bms.controller;

import com.bms.dto.TransferRequest;
import com.bms.dto.TransferResponse;
import com.bms.service.TransferService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/accounts/{accountId}/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    public TransferResponse transfer(
            @AuthenticationPrincipal String username,
            @PathVariable Long accountId,
            @Valid @RequestBody TransferRequest request) {
        return transferService.transfer(username, accountId, request);
    }
}