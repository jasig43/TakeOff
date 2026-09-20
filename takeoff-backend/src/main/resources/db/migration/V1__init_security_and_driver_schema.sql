-- TakeOFF MVP Phase 1: identity, roles and OTP verification.
-- Target: MySQL 8.0+ (also runs on H2 in MySQL mode for the automated tests).
-- All timestamps are stored in UTC.

CREATE TABLE users (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    full_name       VARCHAR(100) NOT NULL,
    email           VARCHAR(254) NOT NULL,
    phone_number    VARCHAR(20)  NOT NULL,
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(30)  NOT NULL,
    phone_verified  BOOLEAN      NOT NULL DEFAULT FALSE,
    enabled         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_email UNIQUE (email),
    CONSTRAINT uk_users_phone_number UNIQUE (phone_number),
    CONSTRAINT chk_users_role CHECK (role IN ('APPLICANT_DRIVER', 'LOGISTICS_ADMIN'))
);

CREATE TABLE otp_tokens (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    user_id     BIGINT       NOT NULL,
    -- 6-digit code in development; HMAC-SHA256 hex digest (64 chars) when hashing is enabled.
    code        VARCHAR(64)  NOT NULL,
    expires_at  DATETIME(6)  NOT NULL,
    consumed    BOOLEAN      NOT NULL DEFAULT FALSE,
    attempts    INT          NOT NULL DEFAULT 0,
    created_at  DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_otp_tokens_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

-- Lookup of a user's latest / active token during verification and resend.
CREATE INDEX idx_otp_tokens_user_active ON otp_tokens (user_id, consumed, id);
-- Supports periodic cleanup of expired tokens.
CREATE INDEX idx_otp_tokens_expires_at ON otp_tokens (expires_at);
