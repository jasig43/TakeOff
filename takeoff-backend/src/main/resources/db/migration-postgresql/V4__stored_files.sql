-- TakeOFF: the bytes of uploaded documents, for deployments without a persistent disk (takeoff.storage.type=database).
-- PostgreSQL edition (hosted demo). Mirrors db/migration/V4__stored_files.sql for MySQL; keep the two in step.

CREATE TABLE stored_files (
    -- server-generated UUID; the same value as application_documents.storage_key
    storage_key VARCHAR(64)  NOT NULL,
    content     BYTEA        NOT NULL,
    size_bytes  BIGINT       NOT NULL,
    created_at  TIMESTAMP(6) NOT NULL,
    PRIMARY KEY (storage_key)
);
