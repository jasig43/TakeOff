-- TakeOFF: administrators can create accounts with a temporary password.
-- must_change_password: set while the password is a temporary one an admin issued; the person can do nothing except
--                       choose their own password until it is cleared.
-- temporary_password_expires_at: when the temporary password stops working (NULL when there is none).
-- All timestamps are stored in UTC.

ALTER TABLE users ADD COLUMN must_change_password BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN temporary_password_expires_at DATETIME(6) NULL;
