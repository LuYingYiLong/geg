package io.github.luyingyilong.ore.debug;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Explicit opt-in on a public getter. Discovery and reflection exist only in oreDebug.
 * Its paired setter must call DebugBridge.previewWrite after validation, before mutation.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Inspectable {
    String key() default "";
    String group() default "Layout";
    /** Public one-argument setter; defaults to the getter's name. */
    String setter() default "";
    double min() default -Double.MAX_VALUE;
    double max() default Double.MAX_VALUE;
}
