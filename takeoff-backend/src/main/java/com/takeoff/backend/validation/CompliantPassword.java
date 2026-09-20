package com.takeoff.backend.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Bean Validation constraint enforcing {@link PasswordPolicy}. Each broken rule is reported as its
 * own violation so clients can show exactly what is missing. {@code null} is treated as valid; pair
 * with {@code @NotNull} when the value is required.
 */
@Documented
@Constraint(validatedBy = PasswordComplianceValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT })
@Retention(RetentionPolicy.RUNTIME)
public @interface CompliantPassword {

	String message() default "Password does not meet the security requirements.";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
