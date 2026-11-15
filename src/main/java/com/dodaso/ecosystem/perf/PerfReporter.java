package com.dodaso.ecosystem.perf;

/** Receives every finished monitored call. Implementations decide what is worth keeping. */
public interface PerfReporter {

  void report(CallResult result);
}
