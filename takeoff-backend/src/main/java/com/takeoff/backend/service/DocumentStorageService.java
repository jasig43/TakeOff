package com.takeoff.backend.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.exception.ApiException;

/**
 * Stores uploaded documents on local disk under random, server-generated names.
 *
 * <p>What makes it safe to accept files from the internet:
 * <ul>
 *   <li>the type is decided from the file's leading bytes (PDF, JPEG or PNG), never from the client-supplied
 *       Content-Type or extension, so a renamed executable is rejected;</li>
 *   <li>the on-disk name is a random UUID validated against a strict pattern, so no user input ever reaches a path
 *       (no traversal), and the original name is kept only as sanitised display text;</li>
 *   <li>a size limit is enforced ({@code takeoff.storage.max-file-bytes}).</li>
 * </ul>
 * MVP limitation: no antivirus scan and local-disk storage only; swap this class for object storage (S3, Azure Blob)
 * before running more than one backend instance.
 */
@Service
public class DocumentStorageService {

	private static final Logger log = LoggerFactory.getLogger(DocumentStorageService.class);
	private static final Pattern KEY_PATTERN = Pattern
		.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

	/** What was stored; {@code contentType} is the detected type, not the client's claim. */
	public record StoredFile(String storageKey, String contentType, long sizeBytes, String displayName) {
	}

	private final Path root;
	private final long maxBytes;

	public DocumentStorageService(TakeoffProperties properties) {
		this.root = Path.of(properties.storage().uploadDir()).toAbsolutePath().normalize();
		this.maxBytes = properties.storage().maxFileBytes();
		try {
			Files.createDirectories(root);
		}
		catch (IOException ex) {
			throw new IllegalStateException("Cannot create the upload directory " + root, ex);
		}
	}

	public StoredFile store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload.");
		}
		if (file.getSize() > maxBytes) {
			throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
					"The file is too large. The maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
		}

		String contentType;
		try (InputStream in = file.getInputStream()) {
			contentType = detectContentType(in.readNBytes(12));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		if (contentType == null) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
					"Upload a PDF, JPG or PNG file.");
		}

		String key = UUID.randomUUID().toString();
		try (InputStream in = file.getInputStream()) {
			Files.copy(in, resolve(key));
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return new StoredFile(key, contentType, file.getSize(), sanitizeFilename(file.getOriginalFilename(), contentType));
	}

	public Resource load(String storageKey) {
		Path path = resolve(storageKey);
		if (!Files.isRegularFile(path)) {
			throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_FILE_MISSING", "The document file could not be found.");
		}
		return new FileSystemResource(path);
	}

	/** Best effort: a leftover file is harmless, a failed delete must never break a request. */
	public void delete(String storageKey) {
		try {
			Files.deleteIfExists(resolve(storageKey));
		}
		catch (IOException | RuntimeException ex) {
			log.warn("Could not delete stored document {}: {}", storageKey, ex.getMessage());
		}
	}

	private Path resolve(String key) {
		if (key == null || !KEY_PATTERN.matcher(key).matches()) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		Path path = root.resolve(key).normalize();
		if (!path.startsWith(root)) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return path;
	}

	/** Recognises PDF, JPEG and PNG by their signatures. Returns null for anything else. */
	static String detectContentType(byte[] h) {
		if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') {
			return "application/pdf";
		}
		if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) {
			return "image/jpeg";
		}
		if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G' && h[4] == 0x0D
				&& h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A) {
			return "image/png";
		}
		return null;
	}

	/** Display-only name: no path parts, no control characters, no leading dots, bounded length. */
	static String sanitizeFilename(String original, String contentType) {
		String name = original == null ? "" : original;
		int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
		if (slash >= 0) {
			name = name.substring(slash + 1);
		}
		name = name.replaceAll("[^\\p{L}\\p{N} ._()\\-]", "_").replaceAll("^\\.+", "").trim();
		if (name.isBlank()) {
			name = "document" + switch (contentType) {
				case "application/pdf" -> ".pdf";
				case "image/png" -> ".png";
				default -> ".jpg";
			};
		}
		return name.length() > 120 ? name.substring(0, 120) : name;
	}
}
