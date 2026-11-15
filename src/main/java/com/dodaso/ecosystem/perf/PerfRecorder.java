package com.dodaso.ecosystem.perf;

import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Starts and ends monitored calls and hands each finished call to the reporters. Shared by the
 * aspect and the request filter so both feed the same stack of calls. Never throws.
 */
public class PerfRecorder {

  private static final Logger LOG = LoggerFactory.getLogger(PerfRecorder.class);

  private final PerfProperties props;
  private final CallTracker tracker;
  private final List<PerfReporter> reporters;

  public PerfRecorder(PerfProperties props, CallTracker tracker, List<PerfReporter> reporters) {
    this.props = props;
    this.tracker = tracker;
    this.reporters = reporters;
  }

  /** True when monitoring is on and this thread is not the incident writer. */
  public boolean active() {
    return props.isEnabled() && !tracker.isSuppressed();
  }

  /** Begins a call, or returns null if monitoring could not start. */
  public CallTracker.Frame begin(
      Layer layer, String className, String methodName, Monitored monitored) {
    try {
      return tracker.begin(layer, className, methodName, monitored);
    } catch (RuntimeException e) {
      LOG.debug("PERF could not start monitoring a call", e);
      return null;
    }
  }

  /** Ends a call begun with {@link #begin}; a null frame is ignored. */
  public void end(CallTracker.Frame frame, Throwable error) {
    if (frame == null) {
      return;
    }
    try {
      CallResult result = tracker.end(frame, error);
      for (PerfReporter reporter : reporters) {
        try {
          reporter.report(result);
        } catch (RuntimeException e) {
          LOG.debug("PERF reporter failed", e);
        }
      }
    } catch (RuntimeException e) {
      LOG.debug("PERF could not finish monitoring a call", e);
    }
  }

  public PerfProperties properties() {
    return props;
  }
}
