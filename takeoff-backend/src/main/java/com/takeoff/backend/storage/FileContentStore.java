package com.takeoff.backend.storage;

import org.springframework.core.io.Resource;

/**
 * Where the bytes of an uploaded document live. {@code DocumentStorageService} decides what may be stored (type, size,
 * name); this only keeps and returns bytes under a server-generated key. Local disk is the default; the hosted demo,
 * whose free servers have no persistent disk, keeps them in the database instead ({@code takeoff.storage.type}).
 */
public interface FileContentStore {

	void write(String key, byte[] content);

	/** @return the content, or null when nothing is stored under that key */
	Resource read(String key);

	/** Best effort: a leftover is harmless, a failed delete must never break a request. */
	void delete(String key);
}
