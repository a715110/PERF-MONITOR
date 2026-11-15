package com.dodaso.ecosystem.perf;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LoggingPerfReporterTest {

  private static final long MS = 1_000_000L;

  private static CallResult call(
      int depth, long wallMs, long cpuMs, long ownWallMs, long ownCpuMs, long wallLimit) {
    return new CallResult(Layer.SERVICE, "ContractService", "save", "A.x > ContractService.save",
        depth, wallMs * MS, cpuMs < 0 ? -1 : cpuMs * MS, ownWallMs * MS,
        ownCpuMs < 0 ? -1 : ownCpuMs * MS, "OK", "http-1", "req-7", "kim", wallLimit, 1000);
  }

  @Test
  void quickCallIsNotReported() {
    assertThat(LoggingPerfReporter.reasons(call(1, 100, 50, 100, 50, 3000), 5000)).isEmpty();
  }

  @Test
  void slowOwnTimeIsReported() {
    assertThat(LoggingPerfReporter.reasons(call(1, 4000, 100, 4000, 100, 3000), 5000))
        .isEqualTo("SLOW_SELF");
  }

  @Test
  void cpuHeavyIsReportedWhenCpuIsKnown() {
    assertThat(LoggingPerfReporter.reasons(call(1, 2000, 1500, 2000, 1500, 3000), 5000))
        .isEqualTo("CPU_HEAVY");
    assertThat(LoggingPerfReporter.reasons(call(1, 2000, -1, 2000, -1, 3000), 5000)).isEmpty();
  }

  @Test
  void slowEntryIsReportedOnlyForTheOutermostCall() {
    assertThat(LoggingPerfReporter.reasons(call(0, 6000, 100, 100, 50, 3000), 5000))
        .isEqualTo("SLOW_ENTRY");
    assertThat(LoggingPerfReporter.reasons(call(1, 6000, 100, 100, 50, 3000), 5000)).isEmpty();
  }

  @Test
  void severalReasonsAreJoined() {
    assertThat(LoggingPerfReporter.reasons(call(0, 9000, 8000, 8000, 7000, 3000), 5000))
        .isEqualTo("SLOW_SELF,CPU_HEAVY,SLOW_ENTRY");
  }

  @Test
  void lineHasTheKeyFacts() {
    String line = LoggingPerfReporter.format(call(1, 4000, 3900, 4000, 3900, 3000), "CPU_HEAVY");
    assertThat(line).contains("PERF CPU_HEAVY", "layer=SERVICE", "call=ContractService.save",
        "kind=CPU", "ownWall=4000ms", "ownCpu=3900ms", "request=req-7", "chain=A.x >");
  }
}
