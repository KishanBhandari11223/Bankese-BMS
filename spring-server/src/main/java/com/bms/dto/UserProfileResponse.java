package com.bms.dto;

public record UserProfileResponse(
        Long userId,
        String username,
        String fullName,
        String email,
        String phone,
        String role
) {}