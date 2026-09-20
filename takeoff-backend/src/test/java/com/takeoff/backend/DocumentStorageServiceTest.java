package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import com.takeoff.backend.config.TakeoffProperties;
import com.takeoff.backend.config.TakeoffProperties.Storage;
import com.takeoff.backend.exception.ApiException;
import com.takeoff.backend.service.DocumentStorageService;
import com.takeoff.backend.service.DocumentStorageService.StoredFile;

class DocumentStorageServiceTest {

	private static final long MAX = 1024; // 1 KB keeps the "too large" test tiny

	private static final byte[] PDF = "%PDF-1.4\nhello".getBytes();
	private static final byte[] PNG = { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0 };
	private static final byte[] JPEG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F' };
	private static final byte[] WINDOWS_EXE = { 'M', 'Z', (byte) 0x90, 0, 3, 0, 0, 0, 4, 0, 0, 0 };

	@TempDir
	Path dir;

	private DocumentStorageService storage;

	@BeforeEach
	void setUp() {
		TakeoffProperties base = TestFixtures.properties(false, false);
		TakeoffProperties properties = new TakeoffProperties(base.jwt(), base.cors(), base.otp(), base.rabbitmq(),
				base.admin(), base.driver(), base.sms(), new Storage(dir.toString(), MAX));
		storage = new DocumentStorageService(properties);
	}

	private static MockMultipartFile upload(String filename, String claimedType, byte[] bytes) {
		return new MockMultipartFile("file", filename, claimedType, bytes);
	}

	@Test
	void acceptsPdfPngAndJpegAndReportsTheDetectedType() throws IOException {
		StoredFile pdf = storage.store(upload("licence.pdf", "application/pdf", PDF));
		StoredFile png = storage.store(upload("id.png", "image/png", PNG));
		StoredFile jpeg = storage.store(upload("photo.jpg", "image/jpeg", JPEG));

		assertThat(pdf.contentType()).isEqualTo("application/pdf");
		assertThat(png.contentType()).isEqualTo("image/png");
		assertThat(jpeg.contentType()).isEqualTo("image/jpeg");
		assertThat(Files.readAllBytes(dir.resolve(pdf.storageKey()))).isEqualTo(PDF);
		assertThat(pdf.sizeBytes()).isEqualTo(PDF.length);
	}

	@Test
	void theTypeComesFromTheBytesNotFromTheClientsClaim() {
		StoredFile stored = storage.store(upload("holiday.png", "image/png", PDF)); // PDF pretending to be a PNG
		assertThat(stored.contentType()).isEqualTo("application/pdf");
	}

	@Test
	void rejectsAnExecutableDisguisedAsAPdf() {
		assertThatThrownBy(() -> storage.store(upload("invoice.pdf", "application/pdf", WINDOWS_EXE)))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE);
				assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE");
			});
		assertThat(filesOnDisk()).isEmpty();
	}

	@Test
	void rejectsPlainTextHtmlAndScripts() {
		for (String content : List.of("just some text", "<html><script>alert(1)</script></html>", "#!/bin/sh\nrm -rf /")) {
			assertThatThrownBy(() -> storage.store(upload("x.pdf", "application/pdf", content.getBytes())))
				.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("UNSUPPORTED_FILE_TYPE"));
		}
	}

	@Test
	void rejectsEmptyAndMissingFiles() {
		assertThatThrownBy(() -> storage.store(upload("empty.pdf", "application/pdf", new byte[0])))
			.isInstanceOfSatisfying(ApiException.class, ex -> assertThat(ex.getCode()).isEqualTo("FILE_REQUIRED"));
		assertThatThrownBy(() -> storage.store(null)).isInstanceOf(ApiException.class);
	}

	@Test
	void rejectsFilesOverTheSizeLimit() {
		byte[] big = Arrays.copyOf(PDF, (int) MAX + 1);
		assertThatThrownBy(() -> storage.store(upload("big.pdf", "application/pdf", big)))
			.isInstanceOfSatisfying(ApiException.class, ex -> {
				assertThat(ex.getStatus()).isEqualTo(HttpStatus.CONTENT_TOO_LARGE);
				assertThat(ex.getCode()).isEqualTo("FILE_TOO_LARGE");
			});
		assertThat(storage.store(upload("ok.pdf", "application/pdf", Arrays.copyOf(PDF, (int) MAX))).sizeBytes())
			.isEqualTo(MAX); // exactly at the limit is fine
	}

	@Test
	void theOnDiskNameIsAServerGeneratedUuidNeverTheUploadedName() {
		StoredFile stored = storage.store(upload("../../etc/passwd.pdf", "application/pdf", PDF));

		assertThat(stored.storageKey()).matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}");
		assertThat(filesOnDisk()).containsExactly(stored.storageKey());
		assertThat(stored.displayName()).isEqualTo("passwd.pdf"); // path parts are stripped from the display name
	}

	@Test
	void displayNamesAreSanitised() {
		assertThat(storage.store(upload("C:\\Users\\me\\scan.pdf", "application/pdf", PDF)).displayName())
			.isEqualTo("scan.pdf");
		assertThat(storage.store(upload("re<po>rt\"|?.pdf", "application/pdf", PDF)).displayName())
			.isEqualTo("re_po_rt___.pdf");
		assertThat(storage.store(upload(".hidden.pdf", "application/pdf", PDF)).displayName()).isEqualTo("hidden.pdf");
		assertThat(storage.store(upload("", "application/pdf", PDF)).displayName()).isEqualTo("document.pdf");
		assertThat(storage.store(upload("a".repeat(300) + ".pdf", "application/pdf", PDF)).displayName())
			.hasSize(120);
	}

	@Test
	void loadRefusesAnythingThatIsNotAGeneratedKey() {
		for (String bad : List.of("../../etc/passwd", "..\\..\\secret", "/etc/passwd", "not-a-uuid", "")) {
			assertThatThrownBy(() -> storage.load(bad)).isInstanceOf(IllegalArgumentException.class);
		}
	}

	@Test
	void loadsWhatWasStoredAndReportsAMissingFile() throws IOException {
		StoredFile stored = storage.store(upload("a.pdf", "application/pdf", PDF));
		assertThat(storage.load(stored.storageKey()).getContentAsByteArray()).isEqualTo(PDF);

		storage.delete(stored.storageKey());
		assertThatThrownBy(() -> storage.load(stored.storageKey())).isInstanceOfSatisfying(ApiException.class,
				ex -> assertThat(ex.getStatus()).isEqualTo(HttpStatus.NOT_FOUND));
	}

	@Test
	void deletingIsBestEffort() {
		assertThatCode(() -> storage.delete("00000000-0000-0000-0000-000000000000")).doesNotThrowAnyException();
		assertThatCode(() -> storage.delete("../../etc/passwd")).doesNotThrowAnyException(); // invalid key is contained
	}

	private List<String> filesOnDisk() {
		try (Stream<Path> files = Files.list(dir)) {
			return files.map(p -> p.getFileName().toString()).toList();
		}
		catch (IOException ex) {
			throw new AssertionError(ex);
		}
	}
}
