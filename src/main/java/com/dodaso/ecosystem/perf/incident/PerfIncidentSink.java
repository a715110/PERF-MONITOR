package com.dodaso.ecosystem.perf.incident;

import java.util.List;

/** Where a batch of incidents goes. The default sends them to the common service. */
public interface PerfIncidentSink {

  /** Returns true when the whole batch was accepted; false asks the writer to retry later. */
  boolean send(List<PerfIncidentDto> batch);
}
