package com.dodaso.ecosystem.perf;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Opt in a method that is outside controllers, services and repositories (a scheduled job, a batch
 * step, a helper), and optionally give it its own limits. A value of -1 keeps the configured
 * default. Methods of classes that are already monitored by layer are not counted twice.
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface Monitored {

  /** Own-time limit in milliseconds, or -1 for the default. */
  long wallMs() default -1;

  /** Own CPU time limit in milliseconds, or -1 for the default. */
  long cpuMs() default -1;
}
