package com.bms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(name = "friends", uniqueConstraints = {
        @UniqueConstraint(name = "uq_friend_account_number",
                columnNames = {"user_id", "recipient_account_number"}),
        @UniqueConstraint(name = "uq_friend_account",
                columnNames = {"user_id", "recipient_account_id"})
})
public class Friend {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_account_id")
    private Account recipientAccount;

    @Column(name = "recipient_account_number", nullable = false, length = 20)
    private String recipientAccountNumber;

    protected Friend() {}

    public Friend(User owner, Account recipientAccount) {
        this.owner = owner;
        this.recipientAccount = recipientAccount;
        this.recipientAccountNumber = recipientAccount.getAccountNumber();
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public Account getRecipientAccount() { return recipientAccount; }
    public String getRecipientAccountNumber() { return recipientAccountNumber; }
}
