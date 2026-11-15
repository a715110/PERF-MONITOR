package com.dodaso.ecosystem.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CallTrackerTest {

  private static void spin(long millis) {
    long end = System.nanoTime() + millis * 1_000_000L;
    while (System.nanoTime() < end) {
      Math.sqrt(System.nanoTime());
    }
  }

  @Test
  void ownTimeLeavesOutChildCalls() throws Exception {
    CallTracker tracker = new CallTracker(new PerfProperties());
    CallTracker.Frame outer = tracker.begin(Layer.SERVICE, "Outer", "run", null);
    Thread.sleep(30);
    CallTracker.Frame inner = tracker.begin(Layer.REPOSITORY, "Inner", "find", null);
    Thread.sleep(120);
    CallResult innerResult = tracker.end(inner, null);
    CallResult outerResult = tracker.end(outer, null);

    assertThat(innerResult.depth()).isEqualTo(1);
    assertThat(outerResult.depth()).isZero();
    assertThat(innerResult.chain()).isEqualTo("Outer.run > Inner.find");
    assertThat(innerResult.ownWallMs()).isGreaterThanOrEqualTo(100);
    assertThat(outerResult.wallMs()).isGreaterThanOrEqualTo(140);
    assertThat(outerResult.ownWallMs()).isLessThan(outerResult.wallMs() - 100);
  }

  @Test
  void sleepingIsWaitAndSpinningIsCpu() throws Exception {
    CallTracker tracker = new CallTracker(new PerfProperties());
    if (!tracker.isCpuSupported()) {
      return;
    }
    CallTracker.Frame waiting = tracker.begin(Layer.SERVICE, "S", "wait", null);
    Thread.sleep(200);
    CallResult waitResult = tracker.end(waiting, null);
    CallTracker.Frame busy = tracker.begin(Layer.SERVICE, "S", "spin", null);
    spin(200);
    CallResult busyResult = tracker.end(busy, null);

    assertThat(waitResult.kind()).isEqualTo("WAIT");
    assertThat(busyResult.kind()).isEqualTo("CPU");
  }

  @Test
  void failedCallIsStillClosedAndNamed() {
    CallTracker tracker = new CallTracker(new PerfProperties());
    CallTracker.Frame f = tracker.begin(Layer.SERVICE, "S", "boom", null);
    CallResult r = tracker.end(f, new IllegalStateException("x"));
    assertThat(r.failed()).isTrue();
    assertThat(r.outcome()).isEqualTo("ERROR IllegalStateException");
    assertThat(tracker.inFlight()).isEmpty();
  }

  @Test
  void inFlightListsRunningCallsOnly() {
    CallTracker tracker = new CallTracker(new PerfProperties());
    CallTracker.Frame a = tracker.begin(Layer.SERVICE, "A", "x", null);
    CallTracker.Frame b = tracker.begin(Layer.REPOSITORY, "B", "y", null);
    assertThat(tracker.inFlight()).hasSize(2);
    tracker.end(b, null);
    assertThat(tracker.inFlight()).containsExactly(a);
    tracker.end(a, null);
    assertThat(tracker.inFlight()).isEmpty();
  }

  @Test
  void limitsOfTheCallAreCarriedInTheResult() {
    PerfProperties p = new PerfProperties();
    p.getOverrides().put("S.slowOne", 12345L);
    CallTracker tracker = new CallTracker(p);
    CallTracker.Frame f = tracker.begin(Layer.SERVICE, "S", "slowOne", null);
    CallResult r = tracker.end(f, null);
    assertThat(r.wallLimitMs()).isEqualTo(12345);
    assertThat(r.cpuLimitMs()).isEqualTo(1000);
  }
}
