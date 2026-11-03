package com.fatoora.invoicing.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.Locale;
import java.util.Set;

/** ISO 3166-1 alpha-2 country code in upper case, e.g. JO, SA, AE. Null is allowed. */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CountryCode.Validator.class)
public @interface CountryCode {

    String message() default "must be a 2-letter country code, e.g. JO";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CountryCode, String> {
        private static final Set<String> CODES = Set.of(Locale.getISOCountries());

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isEmpty() || CODES.contains(value);
        }
    }
}
