package io.github.levij.orderflow.inventory.dto;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

@Documented
@Constraint(validatedBy = InventoryAdjustmentValidator.class)
@Target(TYPE)
@Retention(RUNTIME)
public @interface ValidInventoryAdjustment {

	String message() default "invalid inventory adjustment";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
