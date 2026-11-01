package com.bms.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record SaveFriendRequest(
        @NotBlank
        @Pattern(regexp = "^[0-9]{1,20}$")
        String accountNumber
) {}
