package com.bms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "accounts")
public class Account {

    public enum Type {
        SAVINGS, CURRENT
    }

    public enum Status {
        PENDING_APPROVAL, ACTIVE, FROZEN, CLOSED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner;

    @Column(name = "account_number", nullable = false, unique = true, length = 20)
    private String accountNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false)
    private Type accountType;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO.setScale(2);

    @Column(nullable = false, length = 3, columnDefinition = "CHAR(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.ACTIVE;

    protected Account() {}

    public Account(User owner, String accountNumber, Type accountType) {
        this(owner, accountNumber, accountType, Status.ACTIVE);
    }

    public Account(User owner, String accountNumber, Type accountType, Status status) {
        this.owner = owner;
        this.accountNumber = accountNumber;
        this.accountType = accountType;
        this.currency = "INR";
        this.status = status;
    }

    public Long getId() { return id; }
    public User getOwner() { return owner; }
    public String getAccountNumber() { return accountNumber; }
    public Type getAccountType() { return accountType; }
    public BigDecimal getBalance() { return balance; }
    public String getCurrency() { return currency; }
    public Status getStatus() { return status; }

    public void credit(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
    }

    public void setStatus(Status status) {
        this.status = status;
    }
}