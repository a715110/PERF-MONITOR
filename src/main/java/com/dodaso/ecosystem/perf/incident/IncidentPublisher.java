package com.dodaso.ecosystem.perf.incident;

/** Takes an incident without ever blocking the caller. */
public interface IncidentPublisher {

  void offer(PerfIncidentDto incident);
}
