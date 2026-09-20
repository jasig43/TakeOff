package com.takeoff.backend.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.storage.DiskFileContentStore;
import com.takeoff.backend.storage.FileContentStore;

/**
 * Accepts uploaded documents and keeps them (on local disk, or in the database on the hosted demo) under random,
 * server-generated names.
 *
 * <p>What makes it safe to accept files from the internet:
 * <ul>
 *   <li>the type is decided from the file's leading bytes (PDF, JPEG or PNG), never from the client-supplied
 *       Content-Type or extension, so a renamed executable is rejected;</li>
 *   <li>the storage key is a random UUID validated against a strict pattern, so no user input ever reaches a path
 *       or a query (no traversal), and the original name is kept only as sanitised display text;</li>
 *   <li>a size limit is enforced ({@code takeoff.storage.max-file-bytes}).</li>
 * </ul>
 * MVP limitation: no antivirus scan. Local disk suits one server; for more than one, use the database store or object
 * storage (S3, Azure Blob).
 */
@Service
public class DocumentStorageService {

	private static final Pattern KEY_PATTERN = Pattern
		.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

	/** What was stored; {@code contentType} is the detected type, not the client's claim. */
	public record StoredFile(String storageKey, String contentType, long sizeBytes, String displayName) {
	}

	private final FileContentStore store;
	private final long maxBytes;

	@Autowired
	public DocumentStorageService(TakeoffProperties properties, FileContentStore store) {
		this.store = store;
		this.maxBytes = properties.storage().maxFileBytes();
	}

	/** Local-disk storage, as used by the unit tests and a plain local run. */
	public DocumentStorageService(TakeoffProperties properties) {
		this(properties, new DiskFileContentStore(properties));
	}

	public StoredFile store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new ApiException(HttpStatus.BAD_REQUEST, "FILE_REQUIRED", "Choose a file to upload.");
		}
		if (file.getSize() > maxBytes) {
			throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "FILE_TOO_LARGE",
					"The file is too large. The maximum size is " + (maxBytes / (1024 * 1024)) + " MB.");
		}

		byte[] content;
		try {
			content = file.getBytes(); // already capped at maxBytes above
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		String contentType = detectContentType(java.util.Arrays.copyOf(content, Math.min(content.length, 12)));
		if (contentType == null) {
			throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE",
					"Upload a PDF, JPG or PNG file.");
		}

		String key = UUID.randomUUID().toString();
		store.write(key, content);
		return new StoredFile(key, contentType, content.length, sanitizeFilename(file.getOriginalFilename(), contentType));
	}

	public Resource load(String storageKey) {
		Resource resource = store.read(requireKey(storageKey));
		if (resource == null) {
			throw new ApiException(HttpStatus.NOT_FOUND, "DOCUMENT_FILE_MISSING", "The document file could not be found.");
		}
		return resource;
	}

	/** Best effort: a leftover file is harmless, a failed delete must never break a request. */
	public void delete(String storageKey) {
		try {
			store.delete(requireKey(storageKey));
		}
		catch (RuntimeException ex) {
			// includes an invalid key: nothing legitimate could be stored under it
		}
	}

	private static String requireKey(String key) {
		if (key == null || !KEY_PATTERN.matcher(key).matches()) {
			throw new IllegalArgumentException("Invalid storage key");
		}
		return key;
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
