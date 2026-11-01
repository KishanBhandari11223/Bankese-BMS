package com.bms.dto;

import com.bms.entity.Account;
import com.bms.entity.Friend;

public record FriendResponse(
        Long id,
        String name,
        String accountNumber,
        String accountStatus
) {
    public static FriendResponse from(Friend friend) {
        Account account = friend.getRecipientAccount();
        return new FriendResponse(
                friend.getId(),
                account == null ? "Unavailable recipient"
                        : account.getOwner().getFullName(),
                friend.getRecipientAccountNumber(),
                account == null ? "UNAVAILABLE" : account.getStatus().name());
    }
}
