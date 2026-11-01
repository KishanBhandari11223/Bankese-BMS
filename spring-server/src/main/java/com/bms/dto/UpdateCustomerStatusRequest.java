package com.bms.dto;

import com.bms.entity.User;
import jakarta.validation.constraints.NotNull;

public record UpdateCustomerStatusRequest(@NotNull User.Status status) {
}