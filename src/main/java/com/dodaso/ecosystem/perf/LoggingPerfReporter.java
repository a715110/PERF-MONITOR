package com.dodaso.ecosystem.perf;

import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Writes one WARN line for a call that was slow on its own, burned CPU on its own, or was a slow
 * outermost call. Logger name {@code dodaso.perf}, so it can be sent to its own file.
 */
public class LoggingPerfReporter implements PerfReporter {

  private static final Logger LOG = LoggerFactory.getLogger("dodaso.perf");

  private final PerfProperties props;

  public LoggingPerfReporter(PerfProperties props) {
    this.props = props;
  }

  @Override
  public void report(CallResult r) {
    String reasons = reasons(r, props.getEntryThresholdMs());
    if (!reasons.isEmpty() && LOG.isWarnEnabled()) {
      LOG.warn(format(r, reasons));
    }
  }

  /** Why a call is worth a log line: SLOW_SELF, CPU_HEAVY, SLOW_ENTRY; empty when it is not. */
  public static String reasons(CallResult r, long entryThresholdMs) {
    List<String> out = new ArrayList<>();
    if (r.ownWallMs() >= r.wallLimitMs()) {
      out.add("SLOW_SELF");
    }
    if (r.ownCpuMs() >= 0 && r.ownCpuMs() >= r.cpuLimitMs()) {
      out.add("CPU_HEAVY");
    }
    if (r.depth() == 0 && r.wallMs() >= entryThresholdMs) {
      out.add("SLOW_ENTRY");
    }
    return String.join(",", out);
  }

  static String format(CallResult r, String reasons) {
    StringBuilder sb = new StringBuilder("PERF ").append(reasons)
        .append(" layer=").append(r.layer())
        .append(" call=").append(r.name())
        .append(" kind=").append(r.kind())
        .append(" ownWall=").append(r.ownWallMs()).append("ms")
        .append(" ownCpu=").append(r.ownCpuMs() < 0 ? "n/a" : r.ownCpuMs() + "ms")
        .append(" wall=").append(r.wallMs()).append("ms")
        .append(" cpu=").append(r.cpuMs() < 0 ? "n/a" : r.cpuMs() + "ms")
        .append(" outcome=").append(r.outcome())
        .append(" thread=").append(r.thread());
    if (r.requestId() != null) {
      sb.append(" request=").append(r.requestId());
    }
    if (r.depth() > 0) {
      sb.append(" chain=").append(r.chain());
    }
    return sb.toString();
  }
}
