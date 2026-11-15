package com.dodaso.ecosystem.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import com.dodaso.ecosystem.perf.incident.IncidentPublisher;
import com.dodaso.ecosystem.perf.incident.PerfIncidentDto;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Background look at what is happening now, for the cases a finished-call report cannot show
 * because the call has not finished.
 *
 * <ul>
 *   <li>A monitored call still running after the stuck limit is reported with its thread's stack.
 *   <li>When the process CPU load is at or above the limit, the busiest threads are reported with
 *       the monitored call each one is running and its stack.
 * </ul>
 *
 * <p>The CPU part costs nothing while the load is low: thread CPU times are read only after the
 * load has been high at two checks in a row.
 */
public class PerfWatchdog implements SmartLifecycle {

  private static final Logger LOG = LoggerFactory.getLogger("dodaso.perf");

  private final PerfProperties.Watchdog settings;
  private final CallTracker tracker;
  private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
  private final OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();

  private IncidentPublisher publisher;
  private String serviceName = "";
  private ScheduledExecutorService executor;
  private volatile boolean running;
  private Map<Long, Long> previousCpu;
  private long previousAt;
  private long lastCpuReport;

  public PerfWatchdog(PerfProperties props, CallTracker tracker) {
    this.settings = props.getWatchdog();
    this.tracker = tracker;
  }

  /** Also store watchdog findings as incidents. Optional. */
  public void setPublisher(IncidentPublisher publisher, String serviceName) {
    this.publisher = publisher;
    this.serviceName = serviceName;
  }

  private void publish(Layer layer, String className, String methodName, String reason,
      long wallMs, long cpuMs, String thread, String chain, String text) {
    if (publisher == null) {
      return;
    }
    PerfIncidentDto d = new PerfIncidentDto();
    d.setOccurredAt(LocalDateTime.now(ZoneOffset.UTC));
    d.setService(serviceName);
    d.setReason(reason);
    d.setLayer(layer.name());
    d.setClassName(className);
    d.setMethodName(methodName);
    d.setKind("CPU");
    d.setWallMs(wallMs);
    d.setOwnWallMs(wallMs);
    d.setCpuMs(cpuMs < 0 ? null : cpuMs);
    d.setOutcome("RUNNING");
    d.setThreadName(thread);
    d.setCallChain(chain);
    d.setStackText(text);
    publisher.offer(d);
  }

  @Override
  public void start() {
    if (running) {
      return;
    }
    executor = Executors.newSingleThreadScheduledExecutor(r -> {
      Thread t = new Thread(r, "dodaso-perf-watchdog");
      t.setDaemon(true);
      return t;
    });
    executor.scheduleWithFixedDelay(this::tick, settings.getIntervalMs(), settings.getIntervalMs(),
        TimeUnit.MILLISECONDS);
    running = true;
  }

  @Override
  public void stop() {
    running = false;
    if (executor != null) {
      executor.shutdownNow();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void tick() {
    try {
      reportStuckCalls(System.nanoTime());
      checkCpuLoad(System.nanoTime());
    } catch (RuntimeException e) {
      LOG.debug("PERF watchdog check failed", e);
    }
  }

  /** Reports, per thread, the deepest monitored call that has run longer than the stuck limit. */
  void reportStuckCalls(long now) {
    long stuckNanos = TimeUnit.MILLISECONDS.toNanos(settings.getStuckThresholdMs());
    long repeatNanos = TimeUnit.MILLISECONDS.toNanos(settings.getRepeatMs());
    Map<Long, CallTracker.Frame> deepest = new HashMap<>();
    for (CallTracker.Frame f : tracker.inFlight()) {
      if (now - f.wallStart() < stuckNanos) {
        continue;
      }
      deepest.merge(f.thread().threadId(), f,
          (a, b) -> a.depth() >= b.depth() ? a : b);
    }
    for (CallTracker.Frame f : deepest.values()) {
      if (f.lastReported() != 0 && now - f.lastReported() < repeatNanos) {
        continue;
      }
      f.markReported(now);
      long cpu = tracker.cpuSoFar(f);
      ThreadInfo info = threads.getThreadInfo(f.thread().threadId(), settings.getStackDepth());
      LOG.warn("PERF STILL_RUNNING call={} running={}ms cpuSoFar={} thread={} state={} chain={}{}",
          f.name(), TimeUnit.NANOSECONDS.toMillis(now - f.wallStart()),
          cpu < 0 ? "n/a" : TimeUnit.NANOSECONDS.toMillis(cpu) + "ms", f.thread().getName(),
          info == null ? "gone" : info.getThreadState(), f.chain(), stack(info));
      publish(f.layer, f.className, f.methodName, "STILL_RUNNING",
          TimeUnit.NANOSECONDS.toMillis(now - f.wallStart()),
          cpu < 0 ? -1 : TimeUnit.NANOSECONDS.toMillis(cpu), f.thread().getName(), f.chain(),
          stack(info).stripLeading());
    }
  }

  private void checkCpuLoad(long now) {
    double load = processCpuLoad();
    if (load < settings.getCpuLoadThreshold()) {
      previousCpu = null;
      return;
    }
    Map<Long, Long> current = snapshot();
    if (previousCpu == null) {
      previousCpu = current;
      previousAt = now;
      return;
    }
    long elapsed = Math.max(1L, now - previousAt);
    Map<Long, Long> delta = new HashMap<>();
    for (Map.Entry<Long, Long> e : current.entrySet()) {
      long before = previousCpu.getOrDefault(e.getKey(), e.getValue());
      delta.put(e.getKey(), Math.max(0L, e.getValue() - before));
    }
    previousCpu = current;
    previousAt = now;
    if (lastCpuReport != 0
        && now - lastCpuReport < TimeUnit.MILLISECONDS.toNanos(settings.getCpuReportEveryMs())) {
      return;
    }
    lastCpuReport = now;
    List<Map.Entry<Long, Long>> top = new ArrayList<>(delta.entrySet());
    top.sort(Comparator.comparingLong((Map.Entry<Long, Long> e) -> e.getValue()).reversed());
    StringBuilder sb = new StringBuilder("PERF HIGH_CPU processLoad=")
        .append(Math.round(load * 100)).append("% busiest threads:");
    int shown = 0;
    long self = Thread.currentThread().threadId();
    for (Map.Entry<Long, Long> e : top) {
      if (e.getKey() == self) {
        continue;
      }
      if (shown++ >= settings.getTopThreads() || e.getValue() <= 0) {
        break;
      }
      ThreadInfo info = threads.getThreadInfo(e.getKey(), settings.getStackDepth());
      if (info == null) {
        continue;
      }
      CallTracker.Frame frame = deepestFrameOf(e.getKey());
      sb.append("\n  thread=").append(info.getThreadName())
          .append(" cpu=").append(Math.round(100.0 * e.getValue() / elapsed)).append("% of a core")
          .append(" state=").append(info.getThreadState())
          .append(" monitoredCall=").append(frame == null ? "none" : frame.chain())
          .append(stack(info));
    }
    LOG.warn(sb.toString());
    publish(Layer.OTHER, "JVM", "process", "HIGH_CPU", 0, Math.round(load * 100),
        "watchdog", "process cpu load percent in cpuMs", sb.toString());
  }

  private CallTracker.Frame deepestFrameOf(long threadId) {
    CallTracker.Frame best = null;
    for (CallTracker.Frame f : tracker.inFlight()) {
      if (f.thread().threadId() == threadId && (best == null || f.depth() > best.depth())) {
        best = f;
      }
    }
    return best;
  }

  private Map<Long, Long> snapshot() {
    Map<Long, Long> out = new HashMap<>();
    for (long id : threads.getAllThreadIds()) {
      long cpu = threads.getThreadCpuTime(id);
      if (cpu >= 0) {
        out.put(id, cpu);
      }
    }
    return out;
  }

  /** Process CPU load from 0 to 1, or -1 when the JVM does not offer it. */
  private double processCpuLoad() {
    if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
      return sun.getProcessCpuLoad();
    }
    return -1;
  }

  private static String stack(ThreadInfo info) {
    if (info == null) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    for (StackTraceElement element : info.getStackTrace()) {
      sb.append("\n      at ").append(element);
    }
    return sb.toString();
  }
}
