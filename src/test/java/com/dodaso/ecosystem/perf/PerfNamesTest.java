package com.dodaso.ecosystem.perf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class PerfNamesTest {

  @Test
  void idsAndSessionsAndQueriesAreRemoved() {
    assertThat(PerfNames.path("/api/v1/reassessment/cases/42/survey"))
        .isEqualTo("/api/v1/reassessment/cases/{id}/survey");
    assertThat(PerfNames.path("/api/v1/x/7")).isEqualTo("/api/v1/x/{id}");
    assertThat(PerfNames.path("/contractrecords;jsessionid=ABC123?q=1")).isEqualTo("/contractrecords");
    assertThat(PerfNames.path("/files/3f2a9c1e-1111-2222-3333-444455556666/download"))
        .isEqualTo("/files/{id}/download");
    assertThat(PerfNames.path("/api/v1/pipeline/contract-records?q=abc")).isEqualTo("/api/v1/pipeline/contract-records");
    assertThat(PerfNames.path(null)).isEqualTo("/");
  }

  @Test
  void ignoredPrefixesMatch() {
    List<String> prefixes = new PerfProperties().getIgnoredPaths();
    assertThat(PerfNames.ignored("/actuator/health", prefixes)).isTrue();
    assertThat(PerfNames.ignored("/javax.faces.resource/theme.css", prefixes)).isTrue();
    assertThat(PerfNames.ignored("/contractrecords", prefixes)).isFalse();
  }

  @Test
  void restTargetIsNamedFromServiceAndPath() {
    assertThat(PerfMonitorAspect.describeRest(new Object[] {"elcm_service", "/api/v1/x/12?a=b", null}))
        .isEqualTo("elcm_service /api/v1/x/{id}");
    assertThat(PerfMonitorAspect.describeRest(new Object[0])).isNull();
    assertThat(PerfMonitorAspect.describeRest(null)).isNull();
  }
}
