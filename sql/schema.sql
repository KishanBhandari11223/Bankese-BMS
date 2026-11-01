CREATE DATABASE IF NOT EXISTS bms_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_0900_ai_ci;

USE bms_db;

CREATE TABLE users (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    full_name VARCHAR(120) NOT NULL,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    phone VARCHAR(20),
    role ENUM('CUSTOMER', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    status ENUM('PENDING', 'ACTIVE', 'LOCKED', 'DISABLED')
        NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP
);

CREATE TABLE accounts (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    account_number VARCHAR(20) NOT NULL UNIQUE,
    account_type ENUM('SAVINGS', 'CURRENT')
        NOT NULL DEFAULT 'SAVINGS',
    balance DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency CHAR(3) NOT NULL DEFAULT 'INR',
    status ENUM('PENDING_APPROVAL', 'ACTIVE', 'FROZEN', 'CLOSED')
        NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT chk_account_balance
        CHECK (balance >= 0),

    INDEX idx_accounts_user (user_id)
);

CREATE TABLE beneficiaries (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    beneficiary_name VARCHAR(120) NOT NULL,
    beneficiary_account_number VARCHAR(20) NOT NULL,
    nickname VARCHAR(60),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_beneficiaries_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    UNIQUE KEY uq_beneficiary (
        user_id, beneficiary_account_number
    )
);

CREATE TABLE friends (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    recipient_account_id BIGINT UNSIGNED NULL,
    recipient_account_number VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_friends_user
        FOREIGN KEY (user_id) REFERENCES users(id),

    CONSTRAINT fk_friends_recipient_account
        FOREIGN KEY (recipient_account_id) REFERENCES accounts(id)
        ON DELETE SET NULL,

    UNIQUE KEY uq_friend_account_number (
        user_id, recipient_account_number
    ),

    UNIQUE KEY uq_friend_account (
        user_id, recipient_account_id
    )
);

CREATE TABLE transactions (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    reference_number VARCHAR(36) NOT NULL,
    account_id BIGINT UNSIGNED NOT NULL,
    related_account_id BIGINT UNSIGNED NULL,
    transaction_type ENUM(
        'DEPOSIT',
        'WITHDRAWAL',
        'TRANSFER_IN',
        'TRANSFER_OUT'
    ) NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    balance_after DECIMAL(19,2) NOT NULL,
    description VARCHAR(255),
    status ENUM('PENDING', 'COMPLETED', 'FAILED')
        NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_transactions_account
        FOREIGN KEY (account_id) REFERENCES accounts(id),

    CONSTRAINT fk_transactions_related_account
        FOREIGN KEY (related_account_id) REFERENCES accounts(id),

    CONSTRAINT chk_transaction_amount CHECK (amount > 0),
    CONSTRAINT chk_transaction_balance CHECK (balance_after >= 0),

    INDEX idx_transactions_account_date (account_id, created_at),
    INDEX idx_transactions_reference (reference_number)
);

CREATE TABLE audit_logs (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NULL,
    event_type VARCHAR(80) NOT NULL,
    details JSON NULL,
    ip_address VARCHAR(45),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE SET NULL,

    INDEX idx_audit_user_date (user_id, created_at)
);

CREATE TABLE refresh_tokens (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    revoked_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id) REFERENCES users(id)
        ON DELETE CASCADE,

    INDEX idx_refresh_tokens_user (user_id)
);
