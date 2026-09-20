package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Storage;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.service.DocumentStorageService;
import com.takeoff.backend.service.DocumentStorageService.StoredFile;
import com.takeoff.backend.storage.DatabaseFileContentStore;

/**
 * Documents kept in the database (the hosted demo's storage), against the real schema migration. The same
 * {@code DocumentStorageService} rules apply as on disk: the type comes from the bytes, and the size is capped.
 */
@TakeoffIntegrationTest
class DatabaseFileContentStoreTest {

	private static final long FIVE_MB = 5L * 1024 * 1024;

	@Autowired
	JdbcClient jdbc;

	@MockitoBean
	RabbitTemplate rabbitTemplate;

	private DatabaseFileContentStore store() {
		return new DatabaseFileContentStore(jdbc);
	}

	private DocumentStorageService service(long max) {
		TakeoffProperties base = TestFixtures.properties(false, false);
		return new DocumentStorageService(new TakeoffProperties(base.jwt(), base.cors(), base.otp(), base.rabbitmq(),
				base.admin(), base.driver(), base.sms(), new Storage("unused", max)), store());
	}

	private static byte[] pdf(int size) {
		byte[] bytes = new byte[size];
		new Random(7).nextBytes(bytes); // includes 0x00 and 0xFF, which a text column would mangle
		System.arraycopy("%PDF-1.4\n".getBytes(), 0, bytes, 0, 9);
		return bytes;
	}

	@Test
	void writesAndReadsBackExactlyTheSameBinaryContent() throws IOException {
		String key = UUID.randomUUID().toString();
		byte[] content = pdf(4096);

		store().write(key, content);

		Resource read = store().read(key);
		assertThat(read).isNotNull();
		assertThat(read.getContentAsByteArray()).isEqualTo(content);
	}

	@Test
	void aFiveMegabyteFileSurvivesTheRoundTrip() throws IOException {
		String key = UUID.randomUUID().toString();
		byte[] content = pdf((int) FIVE_MB);

		store().write(key, content);

		assertThat(store().read(key).getContentAsByteArray()).isEqualTo(content);
		store().delete(key);
	}

	@Test
	void anUnknownKeyReadsAsNothing_andDeletingIsIdempotent() {
		String key = UUID.randomUUID().toString();
		assertThat(store().read(key)).isNull();

		store().write(key, pdf(64));
		store().delete(key);
		store().delete(key); // already gone: no error

		assertThat(store().read(key)).isNull();
	}

	@Test
	void aKeyCanBeWrittenOnlyOnce() {
		String key = UUID.randomUUID().toString();
		store().write(key, pdf(64));

		assertThatThrownBy(() -> store().write(key, pdf(65))).isInstanceOf(RuntimeException.class);
		store().delete(key);
	}

	@Test
	void theStorageServiceUsesItLikeDisk_typeFromBytes_sizeCapped_andServesTheDocumentBack() throws IOException {
		DocumentStorageService documents = service(FIVE_MB);
		byte[] content = pdf(2048);

		StoredFile stored = documents.store(new MockMultipartFile("file", "licence.pdf", "image/png", content)); // wrong claim

		assertThat(stored.contentType()).isEqualTo("application/pdf"); // decided from the bytes
		assertThat(documents.load(stored.storageKey()).getContentAsByteArray()).isEqualTo(content);

		documents.delete(stored.storageKey());
		assertThatThrownBy(() -> documents.load(stored.storageKey())).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getCode()).isEqualTo("DOCUMENT_FILE_MISSING"));

		assertThatThrownBy(() -> service(1024).store(new MockMultipartFile("file", "big.pdf", "application/pdf", pdf(2048))))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("FILE_TOO_LARGE"));
		assertThatThrownBy(() -> documents.store(new MockMultipartFile("file", "run.exe", "application/pdf", "MZ......".getBytes())))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE"));
	}

	@Test
	void theSampleTestDocumentsRoundTripThroughTheDatabase() throws IOException {
		Path folder = Path.of("..", "documentation");
		org.junit.jupiter.api.Assumptions.assumeTrue(Files.isDirectory(folder));
		DocumentStorageService documents = service(FIVE_MB);

		try (var files = Files.list(folder)) {
			for (Path file : files.filter(p -> p.getFileName().toString().startsWith("TEST_"))
				.filter(p -> p.getFileName().toString().matches("(?i).*\\.(pdf|png)"))
				.toList()) {
				byte[] bytes = Files.readAllBytes(file);
				StoredFile stored = documents.store(new MockMultipartFile("file", file.getFileName().toString(), "x/y", bytes));
				assertThat(documents.load(stored.storageKey()).getContentAsByteArray()).as(file.toString()).isEqualTo(bytes);
				documents.delete(stored.storageKey());
			}
		}
	}
}
