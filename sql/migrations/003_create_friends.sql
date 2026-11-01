USE bms_db;

CREATE TABLE IF NOT EXISTS friends (
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

INSERT IGNORE INTO friends (
    user_id, recipient_account_id, recipient_account_number
)
SELECT b.user_id, a.id, b.beneficiary_account_number
FROM beneficiaries b
LEFT JOIN accounts a
    ON a.account_number = b.beneficiary_account_number;
