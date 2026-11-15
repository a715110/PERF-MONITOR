package com.dodaso.ecosystem.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class PerfPropertiesTest {

  @Monitored(wallMs = 50, cpuMs = 20)
  void marked() {
  }

  @Monitored
  void markedDefault() {
  }

  private static Monitored on(String method) throws NoSuchMethodException {
    Method m = PerfPropertiesTest.class.getDeclaredMethod(method);
    return m.getAnnotation(Monitored.class);
  }

  @Test
  void layerDefaultsApply() {
    PerfProperties p = new PerfProperties();
    assertThat(p.wallLimitFor(Layer.SERVICE, "A", "b", null)).isEqualTo(3000);
    assertThat(p.wallLimitFor(Layer.CONTROLLER, "A", "b", null)).isEqualTo(3000);
    assertThat(p.wallLimitFor(Layer.REPOSITORY, "A", "b", null)).isEqualTo(1000);
    assertThat(p.cpuLimitFor(null)).isEqualTo(1000);
  }

  @Test
  void overridesBeatDefaultsAndMethodBeatsClass() {
    PerfProperties p = new PerfProperties();
    p.getOverrides().put("ContractService", 8000L);
    p.getOverrides().put("ContractService.save", 20000L);
    assertThat(p.wallLimitFor(Layer.SERVICE, "ContractService", "load", null)).isEqualTo(8000);
    assertThat(p.wallLimitFor(Layer.SERVICE, "ContractService", "save", null)).isEqualTo(20000);
    assertThat(p.wallLimitFor(Layer.SERVICE, "Other", "save", null)).isEqualTo(3000);
  }

  @Test
  void annotationWinsOverEverything() throws Exception {
    PerfProperties p = new PerfProperties();
    p.getOverrides().put("PerfPropertiesTest.marked", 9999L);
    assertThat(p.wallLimitFor(Layer.OTHER, "PerfPropertiesTest", "marked", on("marked")))
        .isEqualTo(50);
    assertThat(p.cpuLimitFor(on("marked"))).isEqualTo(20);
    assertThat(p.wallLimitFor(Layer.OTHER, "X", "y", on("markedDefault"))).isEqualTo(3000);
    assertThat(p.cpuLimitFor(on("markedDefault"))).isEqualTo(1000);
  }

  @Test
  void isOffByDefault() {
    assertThat(new PerfProperties().isEnabled()).isFalse();
  }
}
