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
import java.time.ZoneId;

/**
 * A named IANA time zone such as "Asia/Amman" or "UTC". Fixed offsets like "+03:00" are
 * rejected because they ignore daylight-saving changes. Null is allowed (use @NotNull).
 */
@Documented
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = TimeZoneId.Validator.class)
public @interface TimeZoneId {

    String message() default "must be an IANA time zone, e.g. Asia/Amman";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<TimeZoneId, String> {
        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || ZoneId.getAvailableZoneIds().contains(value);
        }
    }
}
