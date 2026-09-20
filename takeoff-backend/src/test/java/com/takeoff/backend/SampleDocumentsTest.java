package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Storage;
import com.takeoff.backend.service.DocumentStorageService;
import com.takeoff.backend.service.DocumentStorageService.StoredFile;

/**
 * The fictional test documents shipped in {@code documentation/} (TEST_*.pdf and TEST_*.png) must be accepted by the
 * real upload validator, so anyone following {@code TEST_Driver_Profiles_and_Documents.md} can complete the flow.
 */
class SampleDocumentsTest {

	private static final long FIVE_MB = 5L * 1024 * 1024;
	private static final Path FOLDER = Path.of("..", "documentation");

	@TempDir
	Path dir;

	private List<Path> sampleFiles() throws IOException {
		assumeTrue(Files.isDirectory(FOLDER), "documentation/ folder is not next to the backend");
		try (Stream<Path> files = Files.list(FOLDER)) {
			return files.filter(p -> p.getFileName().toString().startsWith("TEST_"))
				.filter(p -> p.getFileName().toString().matches("(?i).*\\.(pdf|png|jpe?g)"))
				.sorted()
				.toList();
		}
	}

	@Test
	void everySampleDocumentIsAcceptedAndRecognisedFromItsBytes() throws IOException {
		TakeoffProperties base = TestFixtures.properties(false, false);
		DocumentStorageService storage = new DocumentStorageService(new TakeoffProperties(base.jwt(), base.cors(),
				base.otp(), base.rabbitmq(), base.admin(), base.driver(), base.sms(), new Storage(dir.toString(), FIVE_MB)));

		List<Path> files = sampleFiles();
		assertThat(files).as("sample documents present").isNotEmpty();

		for (Path file : files) {
			String name = file.getFileName().toString();
			byte[] bytes = Files.readAllBytes(file);
			StoredFile stored = storage.store(new MockMultipartFile("file", name, "application/octet-stream", bytes));

			String expected = name.toLowerCase(Locale.ROOT).endsWith(".pdf") ? "application/pdf" : "image/png";
			assertThat(stored.contentType()).as(name).isEqualTo(expected);
			assertThat(stored.sizeBytes()).as(name).isBetween(1L, FIVE_MB);
			assertThat(stored.displayName()).as("display name of " + name).isEqualTo(name);
		}
	}

	@Test
	void eachTestDriverHasALicenceARegistrationAndAnInsuranceCertificate() throws IOException {
		List<String> names = sampleFiles().stream().map(p -> p.getFileName().toString()).toList();

		for (String prefix : List.of("TEST_01_", "TEST_02_")) {
			assertThat(names).as(prefix).anyMatch(n -> n.startsWith(prefix) && n.contains("_Drivers-Licence."));
			assertThat(names).as(prefix).anyMatch(n -> n.startsWith(prefix) && n.contains("_Vehicle-Registration."));
			assertThat(names).as(prefix).anyMatch(n -> n.startsWith(prefix) && n.contains("_Insurance-Certificate."));
		}
	}
}
