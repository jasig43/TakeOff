package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.takeoff.backend.service.TemporaryPasswordGenerator;
import com.takeoff.backend.validation.PasswordPolicy;

class TemporaryPasswordGeneratorTest {

	private static final int SAMPLES = 2000;
	private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

	@Test
	void everyPasswordMeetsTheSameRulesAsSignUp() {
		for (int i = 0; i < SAMPLES; i++) {
			String password = generator.generate();
			assertThat(PasswordPolicy.violations(password)).as(password).isEmpty();
			assertThat(password).hasSize(18);
		}
	}

	@Test
	void everyPasswordHasEachKindOfCharacterAndNoEasilyMisreadOnes() {
		for (int i = 0; i < SAMPLES; i++) {
			String password = generator.generate();
			assertThat(password).matches(".*[A-Z].*").matches(".*[a-z].*").matches(".*[0-9].*").matches(".*[!@#$%&*\\-_=+?].*");
			assertThat(password).as(password).doesNotContainPattern("[Il1O0]");
			assertThat(password).as(password).matches("[A-Za-z2-9!@#$%&*\\-_=+?]+");
		}
	}

	@Test
	void passwordsAreNotRepeatedAcrossManyDraws() {
		Set<String> seen = new HashSet<>();
		for (int i = 0; i < SAMPLES; i++) {
			seen.add(generator.generate());
		}
		assertThat(seen).hasSize(SAMPLES);
	}

	@Test
	void theGuaranteedCharactersAreShuffledRatherThanAlwaysInTheSamePlaces() {
		Set<Character> firstCharacterKinds = new HashSet<>();
		for (int i = 0; i < 400; i++) {
			char first = generator.generate().charAt(0);
			firstCharacterKinds.add(Character.isUpperCase(first) ? 'U' : Character.isLowerCase(first) ? 'L'
					: Character.isDigit(first) ? 'D' : 'S');
		}
		assertThat(firstCharacterKinds).hasSizeGreaterThanOrEqualTo(3);
	}
}
