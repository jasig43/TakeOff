package com.takeoff.backend.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import com.takeoff.backend.config.TakeoffProperties;

/**
 * Local-disk storage (the default). Keys are validated by the caller, and every path is re-checked to stay inside the
 * upload folder, so nothing a user sends can ever form a path.
 */
@Component
@ConditionalOnProperty(name = "takeoff.storage.type", havingValue = "disk", matchIfMissing = true)
public class DiskFileContentStore implements FileContentStore {

	private static final Logger log = LoggerFactory.getLogger(DiskFileContentStore.class);

	private final Path root;

	public DiskFileContentStore(TakeoffProperties properties) {
		this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
		try {
			Files.createDirectories(root);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot create the upload directory " + root, ex);
		}
	}

	@Override
	public void write(String key, byte[] content) {
		try {
			Files.write(resolve(key), content, StandardOpenOption.CREATE_NEW);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	@Override
	public Resource read(String key) {
		Path path = resolve(key);
		return Files.isRegularFile(path) ? new FileSystemResource(path) : null;
	}

	@Override
	public void delete(String key) {
		try {
			Files.deleteIfExists(resolve(key));
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not delete stored document {}: {}", key, ex.getMessage());
		}
	}

	private Path resolve(String key) {
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return path;
	}
}
