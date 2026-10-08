package io.github.levij.orderflow.order.dto;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = UniqueProductIdsValidator.class)
@Target(FIELD)
@Retention(RUNTIME)
public @interface UniqueProductIds {

	String message() default "must not contain the same product more than once";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
