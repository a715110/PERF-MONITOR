package com.dodaso.ecosystem.perf;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.MDC;

/**
 * Keeps the stack of monitored calls of each thread, works out own time (time minus the monitored
 * calls made from inside), and remembers the calls still running for the watchdog. Plain Java, no
 * Spring, so it can be tested alone.
 */
public class CallTracker {

  private static final int CHAIN_LIMIT = 300;

  /** A monitored call that has begun. */
  public static final class Frame {
    final long id;
    final Thread thread;
    final Layer layer;
    final String className;
    final String methodName;
    final Monitored monitored;
    final long wallStart;
    final long cpuStart;
    final int depth;
    final String chain;
    long childWall;
    long childCpu;
    volatile long lastReported;

    Frame(long id, Thread thread, Layer layer, String className, String methodName,
        Monitored monitored, long wallStart, long cpuStart, int depth, String chain) {
      this.id = id;
      this.thread = thread;
      this.layer = layer;
      this.className = className;
      this.methodName = methodName;
      this.monitored = monitored;
      this.wallStart = wallStart;
      this.cpuStart = cpuStart;
      this.depth = depth;
      this.chain = chain;
    }

    public Thread thread() {
      return thread;
    }

    public String name() {
      return className + "." + methodName;
    }

    public String chain() {
      return chain;
    }

    public int depth() {
      return depth;
    }

    public long wallStart() {
      return wallStart;
    }

    public long cpuStart() {
      return cpuStart;
    }

    public long lastReported() {
      return lastReported;
    }

    public void markReported(long nowNanos) {
      lastReported = nowNanos;
    }
  }

  private final PerfProperties props;
  private final ThreadMXBean threads = ManagementFactory.getThreadMXBean();
  private final boolean cpuSupported;
  private final ThreadLocal<Deque<Frame>> stacks = ThreadLocal.withInitial(ArrayDeque::new);
  private final ConcurrentHashMap<Long, Frame> inFlight = new ConcurrentHashMap<>();
  private final AtomicLong ids = new AtomicLong();
  private final ThreadLocal<Boolean> suppressed = ThreadLocal.withInitial(() -> Boolean.FALSE);

  public CallTracker(PerfProperties props) {
    this.props = props;
    boolean ok = false;
    try {
      if (threads.isCurrentThreadCpuTimeSupported()) {
        if (!threads.isThreadCpuTimeEnabled()) {
          threads.setThreadCpuTimeEnabled(true);
        }
        ok = true;
      }
    } catch (RuntimeException | LinkageError e) {
      ok = false;
    }
    this.cpuSupported = ok;
  }

  /**
   * Makes the current thread invisible to the monitor. The incident writer calls this on its own
   * thread, so saving an incident never creates another incident.
   */
  public void suppressCurrentThread() {
    suppressed.set(Boolean.TRUE);
  }

  public boolean isSuppressed() {
    return suppressed.get();
  }

  public boolean isCpuSupported() {
    return cpuSupported;
  }

  /** Marks the start of a call on the current thread. */
  public Frame begin(Layer layer, String className, String methodName, Monitored monitored) {
    Deque<Frame> stack = stacks.get();
    Frame parent = stack.peek();
    String name = className + "." + methodName;
    String chain = parent == null ? name : parent.chain + " > " + name;
    if (chain.length() > CHAIN_LIMIT) {
      chain = "..." + chain.substring(chain.length() - CHAIN_LIMIT);
    }
    Thread current = Thread.currentThread();
    long cpu = cpuSupported ? threads.getCurrentThreadCpuTime() : -1L;
    Frame frame = new Frame(ids.incrementAndGet(), current, layer, className, methodName,
        monitored, System.nanoTime(), cpu, stack.size(), chain);
    stack.push(frame);
    inFlight.put(frame.id, frame);
    return frame;
  }

  /** Marks the end of a call on the same thread and returns its figures. */
  public CallResult end(Frame frame, Throwable error) {
    long wall = System.nanoTime() - frame.wallStart;
    long cpu = cpuSupported ? Math.max(0L, threads.getCurrentThreadCpuTime() - frame.cpuStart) : -1L;
    Deque<Frame> stack = stacks.get();
    // Pop down to this frame, so a missed end() elsewhere cannot corrupt the stack for good.
    while (!stack.isEmpty()) {
      Frame top = stack.pop();
      inFlight.remove(top.id);
      if (top == frame) {
        break;
      }
    }
    inFlight.remove(frame.id);
    Frame parent = stack.peek();
    if (parent != null) {
      parent.childWall += wall;
      if (cpu >= 0) {
        parent.childCpu += cpu;
      }
    }
    if (stack.isEmpty()) {
      stacks.remove();
    }
    long ownWall = Math.max(0L, wall - frame.childWall);
    long ownCpu = cpu < 0 ? -1L : Math.max(0L, cpu - frame.childCpu);
    String outcome = error == null ? "OK" : "ERROR " + error.getClass().getSimpleName();
    return new CallResult(frame.layer, frame.className, frame.methodName, frame.chain, frame.depth,
        wall, cpu, ownWall, ownCpu, outcome, frame.thread.getName(), MDC.get("requestId"), MDC.get("perfUser"),
        props.wallLimitFor(frame.layer, frame.className, frame.methodName, frame.monitored),
        props.cpuLimitFor(frame.monitored));
  }

  /** Calls that have begun and not ended, on all threads. */
  public Collection<Frame> inFlight() {
    return inFlight.values();
  }

  /** CPU time used so far by the thread of this call since it began, or -1. */
  public long cpuSoFar(Frame frame) {
    if (!cpuSupported || frame.cpuStart < 0) {
      return -1L;
    }
    long now = threads.getThreadCpuTime(frame.thread.threadId());
    return now < 0 ? -1L : Math.max(0L, now - frame.cpuStart);
  }
}
