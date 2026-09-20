package com.takeoff.backend;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.takeoff.backend.dto.SignUpRequest;
import com.takeoff.backend.validation.PasswordPolicy;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class PasswordPolicyTest {

	private static ValidatorFactory factory;
	private static Validator validator;

	@BeforeAll
	static void setUp() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void tearDown() {
		factory.close();
	}

	private static Set<ConstraintViolation<SignUpRequest>> validate(String password) {
		return validator.validate(new SignUpRequest("Test Driver", "driver@example.com", "+15550111", password, true));
	}

	@Test
	void compliantPasswordPasses() {
		assertThat(PasswordPolicy.isCompliant("Sturdy#Password2026")).isTrue();
		assertThat(validate("Sturdy#Password2026")).isEmpty();
	}

	@Test
	void passwordOfExactlyFifteenCharactersPasses() {
		String password = "Abcdefghijklm!n";
		assertThat(password).hasSize(15);
		assertThat(PasswordPolicy.isCompliant(password)).isTrue();
	}

	@Test
	void passwordShorterThanFifteenCharactersIsRejected() {
		String password = "Abcdefghijkl!n"; // 14 characters, otherwise compliant
		assertThat(PasswordPolicy.violations(password)).singleElement().asString().contains("at least 15 characters");

		Set<ConstraintViolation<SignUpRequest>> violations = validate(password);
		assertThat(violations).singleElement().satisfies(v -> {
			assertThat(v.getPropertyPath()).hasToString("password");
			assertThat(v.getMessage()).contains("at least 15 characters");
		});
	}

	@Test
	void passwordWithoutUppercaseLetterIsRejected() {
		String password = "all-lowercase-but-long!";
		assertThat(PasswordPolicy.violations(password)).singleElement().asString().contains("uppercase");
		assertThat(validate(password)).singleElement().extracting(ConstraintViolation::getMessage).asString()
			.contains("uppercase");
	}

	@Test
	void passwordWithoutSpecialCharacterIsRejected() {
		String password = "NoSpecialCharactersHere123";
		assertThat(PasswordPolicy.violations(password)).singleElement().asString().contains("special character");
		assertThat(validate(password)).singleElement().extracting(ConstraintViolation::getMessage).asString()
			.contains("special character");
	}

	@Test
	void everyViolatedRuleIsReportedSeparately() {
		assertThat(PasswordPolicy.violations("short")).hasSize(3);
		// The special-character list contains { } $ which are interpolation metacharacters in violation
		// messages; they must be escaped so the message renders literally instead of failing.
		Set<ConstraintViolation<SignUpRequest>> violations = validate("short");
		assertThat(violations).hasSize(3);
		assertThat(violations).anySatisfy(v -> assertThat(v.getMessage()).contains("!@#$%^&*()_+-=[]{}|;:,.<>?"));
	}

	@ParameterizedTest
	@ValueSource(strings = { "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "-", "=", "[", "]", "{", "}",
			"|", ";", ":", ",", ".", "<", ">", "?" })
	void everyDocumentedSpecialCharacterIsAccepted(String special) {
		assertThat(PasswordPolicy.isCompliant("Abcdefghijklmn" + special)).isTrue();
	}

	@ParameterizedTest
	@ValueSource(strings = { " ", "~", "`", "'", "\"", "\\", "/" })
	void charactersOutsideTheDocumentedSetAreNotSpecial(String other) {
		assertThat(PasswordPolicy.violations("Abcdefghijklmn" + other)).anyMatch(v -> v.contains("special character"));
	}

	@Test
	void passwordBeyondTheBcryptByteLimitIsRejectedExplicitly() {
		String tooLong = "A!" + "x".repeat(71); // 73 bytes
		assertThat(PasswordPolicy.violations(tooLong)).singleElement().asString().contains("72 bytes");
	}

	@Test
	void nullPasswordIsRejectedByNotNullNotByTheComplianceRule() {
		assertThat(validate(null)).singleElement().extracting(ConstraintViolation::getMessage)
			.isEqualTo("Password is required.");
	}
}
