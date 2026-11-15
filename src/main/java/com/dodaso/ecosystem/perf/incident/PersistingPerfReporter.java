package com.dodaso.ecosystem.perf.incident;

import com.dodaso.ecosystem.perf.CallResult;
import com.dodaso.ecosystem.perf.LoggingPerfReporter;
import com.dodaso.ecosystem.perf.PerfProperties;
import com.dodaso.ecosystem.perf.PerfReporter;

/** Queues an incident for storage when a finished call is slow or heavy (same rules as the log). */
public class PersistingPerfReporter implements PerfReporter {

  private final PerfProperties props;
  private final IncidentPublisher publisher;
  private final String service;

  public PersistingPerfReporter(PerfProperties props, IncidentPublisher publisher, String service) {
    this.props = props;
    this.publisher = publisher;
    this.service = service;
  }

  @Override
  public void report(CallResult r) {
    String reasons = LoggingPerfReporter.reasons(r, props.getEntryThresholdMs());
    if (!reasons.isEmpty()) {
      publisher.offer(PerfIncidentDto.from(r, reasons, service));
    }
  }
}
