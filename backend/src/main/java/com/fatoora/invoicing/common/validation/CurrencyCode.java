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
import java.util.Currency;

/** A valid ISO 4217 currency code in upper case, e.g. USD, JOD, JPY. Null is allowed (use @NotNull). */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = CurrencyCode.Validator.class)
public @interface CurrencyCode {

    String message() default "must be an ISO 4217 currency code, e.g. USD";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<CurrencyCode, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            if (value == null) {
                return true;
            }
            if (!value.matches("[A-Z]{3}")) {
                return false;
            }
            try {
                Currency.getInstance(value);
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }
    }
}
