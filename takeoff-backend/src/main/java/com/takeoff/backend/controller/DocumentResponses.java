package com.takeoff.backend.controller;

import java.nio.charset.StandardCharsets;

import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.takeoff.backend.service.DocumentDownload;

/** Builds the response for streaming a stored document. */
final class DocumentResponses {

	private DocumentResponses() {
	}

	/**
	 * The type is the one detected at upload time (PDF/JPEG/PNG), served with {@code nosniff} so a browser cannot
	 * reinterpret it, and never cached (these are identity documents).
	 */
	static ResponseEntity<Resource> inline(DocumentDownload download) {
		return ResponseEntity.ok()
			.contentType(MediaType.parseMediaType(download.contentType()))
			.header(HttpHeaders.CONTENT_DISPOSITION,
					ContentDisposition.inline().filename(download.filename(), StandardCharsets.UTF_8).build().toString())
			.header("X-Content-Type-Options", "nosniff")
			.cacheControl(CacheControl.noStore().cachePrivate())
			.body(download.resource());
	}
}
