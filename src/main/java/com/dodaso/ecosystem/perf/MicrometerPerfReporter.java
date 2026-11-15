package com.dodaso.ecosystem.perf;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.function.Supplier;

/**
 * Records two timers per method. {@code dodaso.perf.call} is the total elapsed time, with an
 * outcome tag. {@code dodaso.perf.cpu} is the own CPU time, so summing it over methods never
 * counts the same CPU twice; sort it to see which methods use the most CPU.
 */
public class MicrometerPerfReporter implements PerfReporter {

  private final Supplier<MeterRegistry> registries;

  /** The registry is looked up at the first call, so start-up order does not matter. */
  public MicrometerPerfReporter(Supplier<MeterRegistry> registries) {
    this.registries = registries;
  }

  @Override
  public void report(CallResult r) {
    MeterRegistry registry = registries.get();
    if (registry == null) {
      return;
    }
    Timer.builder("dodaso.perf.call")
        .tag("layer", r.layer().name())
        .tag("class", r.className())
        .tag("method", r.methodName())
        .tag("outcome", r.failed() ? "error" : "ok")
        .register(registry)
        .record(Duration.ofNanos(r.wallNanos()));
    if (r.ownCpuNanos() >= 0) {
      Timer.builder("dodaso.perf.cpu")
          .tag("layer", r.layer().name())
          .tag("class", r.className())
          .tag("method", r.methodName())
          .register(registry)
          .record(Duration.ofNanos(r.ownCpuNanos()));
    }
  }
}
