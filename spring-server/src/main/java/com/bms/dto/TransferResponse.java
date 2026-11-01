package com.bms.dto;

import java.math.BigDecimal;

public record TransferResponse(
        String referenceNumber,
        String sourceAccountNumber,
        String destinationAccountNumber,
        BigDecimal amount,
        BigDecimal sourceBalance,
        BigDecimal destinationBalance,
        String status
) {}