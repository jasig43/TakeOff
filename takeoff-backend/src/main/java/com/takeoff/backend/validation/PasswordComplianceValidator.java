package com.takeoff.backend.validation;

import java.util.List;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordComplianceValidator implements ConstraintValidator<CompliantPassword, String> {

	@Override
	public boolean isValid(String value, ConstraintValidatorContext context) {
		if (value == null) {
			return true;
		}
		List<String> violations = PasswordPolicy.violations(value);
		if (violations.isEmpty()) {
			return true;
		}
		context.disableDefaultConstraintViolation();
		for (String violation : violations) {
			context.buildConstraintViolationWithTemplate(escapeForTemplate(violation)).addConstraintViolation();
		}
		return false;
	}

	/**
	 * Constraint messages are interpolation templates: braces, dollar signs and backslashes are special.
	 * The special-character list contains all of them, so they are escaped to be shown literally.
	 */
	static String escapeForTemplate(String message) {
		StringBuilder escaped = new StringBuilder(message.length() + 8);
		for (char c : message.toCharArray()) {
			if (c == '\\' || c == '{' || c == '}' || c == '$') {
				escaped.append('\\');
			}
			escaped.append(c);
		}
		return escaped.toString();
	}
}
