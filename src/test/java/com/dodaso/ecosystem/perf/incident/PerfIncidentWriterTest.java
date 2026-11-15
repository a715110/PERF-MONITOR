package com.dodaso.ecosystem.perf.incident;

import static org.assertj.core.api.Assertions.assertThat;

import com.dodaso.ecosystem.perf.CallTracker;
import com.dodaso.ecosystem.perf.PerfProperties;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class PerfIncidentWriterTest {

  private static PerfIncidentDto incident(String name) {
    PerfIncidentDto d = new PerfIncidentDto();
    d.setClassName(name);
    d.setMethodName("m");
    return d;
  }

  private static void await(java.util.function.BooleanSupplier done) throws Exception {
    long end = System.currentTimeMillis() + 5000;
    while (!done.getAsBoolean() && System.currentTimeMillis() < end) {
      Thread.sleep(20);
    }
  }

  private PerfProperties props() {
    PerfProperties p = new PerfProperties();
    p.getStore().setFlushIntervalMs(50);
    p.getStore().setBatchSize(3);
    return p;
  }

  @Test
  void incidentsAreSentInBatchesOfTheConfiguredSize() throws Exception {
    List<Integer> sizes = Collections.synchronizedList(new ArrayList<>());
    PerfProperties p = props();
    PerfIncidentWriter w = new PerfIncidentWriter(p, batch -> sizes.add(batch.size()) || true,
        new CallTracker(p));
    for (int i = 0; i < 7; i++) {
      w.offer(incident("c" + i));
    }
    w.start();
    await(() -> w.sent() == 7);
    w.stop();
    assertThat(w.sent()).isEqualTo(7);
    assertThat(sizes).allMatch(n -> n <= 3);
  }

  @Test
  void fullQueueDropsNewIncidentsAndCountsThem() {
    PerfProperties p = props();
    p.getStore().setQueueSize(10);
    PerfIncidentWriter w = new PerfIncidentWriter(p, batch -> true, new CallTracker(p));
    for (int i = 0; i < 25; i++) {
      w.offer(incident("c" + i));
    }
    assertThat(w.waiting()).isEqualTo(10);
    assertThat(w.dropped()).isEqualTo(15);
  }

  @Test
  void aFailingSinkIsRetriedThenTheBatchIsDropped() throws Exception {
    AtomicInteger calls = new AtomicInteger();
    PerfProperties p = props();
    PerfIncidentWriter w = new PerfIncidentWriter(p, batch -> {
      calls.incrementAndGet();
      throw new IllegalStateException("common service down");
    }, new CallTracker(p));
    w.offer(incident("a"));
    w.start();
    await(() -> w.dropped() == 1);
    w.stop();
    assertThat(calls.get()).isEqualTo(PerfIncidentWriter.MAX_ATTEMPTS);
    assertThat(w.dropped()).isEqualTo(1);
    assertThat(w.sent()).isZero();
  }

  @Test
  void stackTextIsTrimmedOrRemovedAsConfigured() {
    PerfProperties p = props();
    p.getStore().setMaxStackChars(10);
    PerfIncidentWriter w = new PerfIncidentWriter(p, batch -> true, new CallTracker(p));
    PerfIncidentDto d = incident("x");
    d.setStackText("0123456789ABCDEF");
    w.offer(d);
    assertThat(d.getStackText()).isEqualTo("0123456789");
    p.getStore().setIncludeStack(false);
    PerfIncidentDto e = incident("y");
    e.setStackText("abc");
    w.offer(e);
    assertThat(e.getStackText()).isNull();
  }

  @Test
  void theWriterThreadIsInvisibleToTheMonitor() throws Exception {
    PerfProperties p = props();
    CallTracker tracker = new CallTracker(p);
    boolean[] sawSuppressed = new boolean[1];
    PerfIncidentWriter w = new PerfIncidentWriter(p, batch -> {
      sawSuppressed[0] = tracker.isSuppressed();
      return true;
    }, tracker);
    w.offer(incident("a"));
    w.start();
    await(() -> w.sent() == 1);
    w.stop();
    assertThat(sawSuppressed[0]).isTrue();
    assertThat(tracker.isSuppressed()).isFalse();
  }
}
