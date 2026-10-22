package io.github.luyingyilong.ore.gui;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicit resource configuration permission, independent of Inspector editability.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UiProperty {
    String value() default "";

    String setter() default "";

    String silentSetter() default "";

    boolean initialState() default false;
}
