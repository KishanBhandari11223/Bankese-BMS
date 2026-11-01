package com.bms.dto;

import com.bms.entity.User;
import java.time.Instant;

public record RegisterResponse(
        Long id,
        String fullName,
        String username,
        String email,
        String role,
        String status,
        Instant createdAt
) {
    public static RegisterResponse from(User user) {
        return new RegisterResponse(
                user.getId(),
                user.getFullName(),
                user.getUsername(),
                user.getEmail(),
                user.getRole().name(),
                user.getStatus().name(),
                user.getCreatedAt()
        );
    }
}