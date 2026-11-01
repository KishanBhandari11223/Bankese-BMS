package com.bms.dto;

import java.util.List;

public record AdminCustomerResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        String phone,
        String role,
        String status,
        List<AccountResponse> accounts
) {
}