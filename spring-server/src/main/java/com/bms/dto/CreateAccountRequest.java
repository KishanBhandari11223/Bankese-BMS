package com.bms.dto;

import com.bms.entity.Account;
import jakarta.validation.constraints.NotNull;

public record CreateAccountRequest(@NotNull Account.Type accountType) {}