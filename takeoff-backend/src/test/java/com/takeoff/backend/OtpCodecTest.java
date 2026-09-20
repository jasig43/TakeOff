package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import com.takeoff.backend.service.OtpCodec;

class OtpCodecTest {

	@Test
	void plainModeStoresTheCodeAsIs() {
		OtpCodec codec = new OtpCodec(TestFixtures.properties(false, false), TestFixtures.environment("dev"));
		assertThat(codec.encode(1L, "123456")).isEqualTo("123456");
		assertThat(codec.matches(1L, "123456", "123456")).isTrue();
		assertThat(codec.matches(1L, "654321", "123456")).isFalse();
	}

	@Test
	void hashedModeStoresAKeyedDigestBoundToTheUser() {
		OtpCodec codec = new OtpCodec(TestFixtures.properties(true, false), TestFixtures.environment("prod"));

		String stored = codec.encode(1L, "123456");

		assertThat(stored).hasSize(64).matches("[0-9a-f]{64}").doesNotContain("123456");
		assertThat(codec.matches(1L, "123456", stored)).isTrue();
		assertThat(codec.matches(1L, "123457", stored)).isFalse();
		// The same code for a different user hashes differently, so equal codes can't be correlated across users.
		assertThat(codec.encode(2L, "123456")).isNotEqualTo(stored);
		assertThat(codec.matches(2L, "123456", stored)).isFalse();
	}

	@Test
	void hashingCannotBeDisabledOutsideDevOrTest() {
		assertThatThrownBy(() -> new OtpCodec(TestFixtures.properties(false, false), TestFixtures.environment("prod")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("hash-codes=false");
	}
}
