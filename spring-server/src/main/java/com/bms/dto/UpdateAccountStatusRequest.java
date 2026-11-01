package com.bms.dto;

import com.bms.entity.Account;
import jakarta.validation.constraints.NotNull;

public record UpdateAccountStatusRequest(@NotNull Account.Status status) {
}