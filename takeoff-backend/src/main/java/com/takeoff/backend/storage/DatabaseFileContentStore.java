package com.takeoff.backend.storage;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Keeps document bytes in the {@code stored_files} table, next to everything else about the application. Used on the
 * hosted demo, whose free servers lose their disk on every restart. Plain SQL rather than a JPA entity so the same code
 * works on MySQL (LONGBLOB) and PostgreSQL (BYTEA) without Hibernate's schema check caring which binary type it is.
 *
 * <p>Documents are capped at a few megabytes (see {@code takeoff.storage.max-file-bytes}), so reading one whole into
 * memory to serve it is fine.
 */
@Component
@ConditionalOnProperty(name = "takeoff.storage.type", havingValue = "database")
public class DatabaseFileContentStore implements FileContentStore {

	private static final Logger log = LoggerFactory.getLogger(DatabaseFileContentStore.class);

	private final JdbcClient jdbc;

	public DatabaseFileContentStore(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void write(String key, byte[] content) {
		jdbc.sql("insert into stored_files (storage_key, content, size_bytes, created_at) "
				+ "values (:key, :content, :size, :createdAt)")
			.param("key", key)
			.param("content", content)
			.param("size", (long) content.length)
			.param("createdAt", LocalDateTime.now(ZoneOffset.UTC))
			.update();
	}

	@Override
	public Resource read(String key) {
		return jdbc.sql("select content from stored_files where storage_key = :key")
			.param("key", key)
			.query((rs, row) -> rs.getBytes(1))
			.optional()
			.<Resource>map(ByteArrayResource::new)
			.orElse(null);
	}

	@Override
	public void delete(String key) {
		try {
			jdbc.sql("delete from stored_files where storage_key = :key").param("key", key).update();
		}
		catch (RuntimeException ex) {
			log.warn("Could not delete stored document {}: {}", key, ex.getMessage());
		}
	}
}
