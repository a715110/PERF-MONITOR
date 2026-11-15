package com.dodaso.ecosystem.perf.incident;

import com.dodaso.ecosystem.perf.CallTracker;
import com.dodaso.ecosystem.perf.PerfProperties;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * Collects incidents on a bounded queue and sends them in batches from its own thread, so a slow
 * or unreachable common service can never slow a request. When the queue is full new incidents are
 * dropped and counted. A batch that cannot be sent is tried again on the next round, up to
 * {@value #MAX_ATTEMPTS} times, then dropped. The incident is always in the log as well.
 */
public class PerfIncidentWriter implements IncidentPublisher, SmartLifecycle {

  static final int MAX_ATTEMPTS = 3;
  private static final Logger LOG = LoggerFactory.getLogger("dodaso.perf");

  private final PerfProperties.Store settings;
  private final PerfIncidentSink sink;
  private final CallTracker tracker;
  private final BlockingQueue<PerfIncidentDto> queue;
  private final AtomicLong dropped = new AtomicLong();
  private final AtomicLong sent = new AtomicLong();

  private Thread thread;
  private volatile boolean running;
  private long lastWarnAt;

  public PerfIncidentWriter(PerfProperties props, PerfIncidentSink sink, CallTracker tracker) {
    this.settings = props.getStore();
    this.sink = sink;
    this.tracker = tracker;
    this.queue = new ArrayBlockingQueue<>(Math.max(10, settings.getQueueSize()));
  }

  @Override
  public void offer(PerfIncidentDto incident) {
    if (incident == null) {
      return;
    }
    trim(incident);
    if (!queue.offer(incident)) {
      dropped.incrementAndGet();
    }
  }

  private void trim(PerfIncidentDto d) {
    String stack = d.getStackText();
    if (stack == null) {
      return;
    }
    if (!settings.isIncludeStack()) {
      d.setStackText(null);
    } else if (stack.length() > settings.getMaxStackChars()) {
      d.setStackText(stack.substring(0, settings.getMaxStackChars()));
    }
  }

  public long dropped() {
    return dropped.get();
  }

  public long sent() {
    return sent.get();
  }

  public int waiting() {
    return queue.size();
  }

  @Override
  public void start() {
    if (running) {
      return;
    }
    running = true;
    thread = new Thread(this::loop, "dodaso-perf-writer");
    thread.setDaemon(true);
    thread.start();
  }

  @Override
  public void stop() {
    running = false;
    if (thread != null) {
      thread.interrupt();
    }
  }

  @Override
  public boolean isRunning() {
    return running;
  }

  private void loop() {
    tracker.suppressCurrentThread();
    List<PerfIncidentDto> pending = new ArrayList<>();
    int attempts = 0;
    while (running) {
      try {
        if (pending.isEmpty()) {
          PerfIncidentDto first = queue.poll(settings.getFlushIntervalMs(), TimeUnit.MILLISECONDS);
          if (first == null) {
            continue;
          }
          pending.add(first);
          queue.drainTo(pending, Math.max(1, settings.getBatchSize()) - 1);
          attempts = 0;
        }
        if (trySend(pending)) {
          sent.addAndGet(pending.size());
          pending = new ArrayList<>();
        } else if (++attempts >= MAX_ATTEMPTS) {
          dropped.addAndGet(pending.size());
          pending = new ArrayList<>();
        } else {
          Thread.sleep(settings.getFlushIntervalMs());
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException e) {
        LOG.debug("PERF writer round failed", e);
      }
    }
  }

  private boolean trySend(List<PerfIncidentDto> batch) {
    try {
      return sink.send(batch);
    } catch (RuntimeException e) {
      long now = System.currentTimeMillis();
      if (now - lastWarnAt > 60_000) {
        lastWarnAt = now;
        LOG.warn("PERF could not store {} incidents, will retry: {}", batch.size(), e.getMessage());
      }
      return false;
    }
  }
}
