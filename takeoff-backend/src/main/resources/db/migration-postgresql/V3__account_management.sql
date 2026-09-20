-- TakeOFF: administrators can create accounts with a temporary password. PostgreSQL edition (hosted demo).
-- Mirrors db/migration/V3__account_management.sql for MySQL; keep the two in step. All timestamps are UTC.

ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN temporary_password_expires_at TIMESTAMP(6) NULL;
