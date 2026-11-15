package com.dodaso.ecosystem.perf;

/**
 * One finished monitored call. Times are in nanoseconds. "Own" values leave out the monitored
 * calls this call made. A cpu value of -1 means the JVM cannot measure thread CPU time.
 *
 * @param layer kind of code
 * @param className simple class name
 * @param methodName method name
 * @param chain callers from the outermost call down to this one, such as {@code A.x > B.y}
 * @param depth 0 for an outermost call
 * @param wallNanos total elapsed time
 * @param cpuNanos total CPU time of the thread
 * @param ownWallNanos elapsed time without monitored child calls
 * @param ownCpuNanos CPU time without monitored child calls, or -1
 * @param outcome OK, or ERROR followed by the exception class name
 * @param thread thread name
 * @param requestId value of the MDC key requestId, or null
 * @param user value of the MDC key perfUser (the signed-in user), or null
 * @param wallLimitMs own-time limit that applied
 * @param cpuLimitMs own CPU limit that applied
 */
public record CallResult(
    Layer layer,
    String className,
    String methodName,
    String chain,
    int depth,
    long wallNanos,
    long cpuNanos,
    long ownWallNanos,
    long ownCpuNanos,
    String outcome,
    String thread,
    String requestId,
    String user,
    long wallLimitMs,
    long cpuLimitMs) {

  private static final long MS = 1_000_000L;

  public String name() {
    return className + "." + methodName;
  }

  public long wallMs() {
    return wallNanos / MS;
  }

  public long cpuMs() {
    return cpuNanos < 0 ? -1 : cpuNanos / MS;
  }

  public long ownWallMs() {
    return ownWallNanos / MS;
  }

  public long ownCpuMs() {
    return ownCpuNanos < 0 ? -1 : ownCpuNanos / MS;
  }

  public boolean failed() {
    return !"OK".equals(outcome);
  }

  /** CPU or WAIT: whether the call mostly burned CPU or mostly waited (database, network). */
  public String kind() {
    if (ownCpuNanos < 0 || ownWallNanos <= 0) {
      return "UNKNOWN";
    }
    double share = (double) ownCpuNanos / ownWallNanos;
    if (share >= 0.7) {
      return "CPU";
    }
    return share <= 0.25 ? "WAIT" : "MIXED";
  }
}
