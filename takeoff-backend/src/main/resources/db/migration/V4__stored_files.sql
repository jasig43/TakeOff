-- TakeOFF: the bytes of uploaded documents, for deployments without a persistent disk (takeoff.storage.type=database).
-- The table always exists; it stays empty when documents are kept on local disk.
-- Target: MySQL 8.0+ (also runs on H2 in MySQL mode for the automated tests). All timestamps are UTC.

CREATE TABLE stored_files (
    -- server-generated UUID; the same value as application_documents.storage_key
    storage_key VARCHAR(64) NOT NULL,
    content     LONGBLOB    NOT NULL,
    size_bytes  BIGINT      NOT NULL,
    created_at  DATETIME(6) NOT NULL,
    PRIMARY KEY (storage_key)
);
