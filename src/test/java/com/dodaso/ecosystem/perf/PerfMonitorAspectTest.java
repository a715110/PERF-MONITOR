package com.dodaso.ecosystem.perf;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.aop.aspectj.annotation.AspectJProxyFactory;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;

class PerfMonitorAspectTest {

  @Repository
  static class Dao {
    String find() throws InterruptedException {
      Thread.sleep(20);
      return "row";
    }
  }

  @Service
  static class Svc {
    private final Dao dao;

    Svc(Dao dao) {
      this.dao = dao;
    }

    String load() throws InterruptedException {
      return dao.find();
    }

    String fail() {
      throw new IllegalArgumentException("bad");
    }
  }

  @Monitored(wallMs = 5)
  static class Job {
    void run() {
    }
  }

  private final PerfProperties props = new PerfProperties();
  private final List<CallResult> seen = new ArrayList<>();

  private <T> T proxy(T target) {
    props.setEnabled(true);
    PerfMonitorAspect aspect =
        new PerfMonitorAspect(props, new CallTracker(props), List.of(seen::add));
    AspectJProxyFactory factory = new AspectJProxyFactory(target);
    factory.setProxyTargetClass(true);
    factory.addAspect(aspect);
    return factory.getProxy();
  }

  @Test
  void serviceCallingRepositoryGivesTwoLinkedResults() throws Exception {
    Dao dao = proxy(new Dao());
    Svc svc = proxy(new Svc(dao));

    assertThat(svc.load()).isEqualTo("row");

    assertThat(seen).hasSize(2);
    CallResult inner = seen.get(0);
    CallResult outer = seen.get(1);
    assertThat(inner.layer()).isEqualTo(Layer.REPOSITORY);
    assertThat(outer.layer()).isEqualTo(Layer.SERVICE);
    assertThat(inner.chain()).contains("Svc.load > Dao.find");
    assertThat(outer.depth()).isZero();
  }

  @Test
  void exceptionPassesThroughUnchangedAndIsReported() {
    Svc svc = proxy(new Svc(new Dao()));

    assertThatThrownBy(svc::fail).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("bad");
    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).failed()).isTrue();
  }

  @Test
  void switchedOffMeansNothingIsRecorded() throws Exception {
    Svc svc = proxy(new Svc(new Dao()));
    props.setEnabled(false);

    svc.load();

    assertThat(seen).isEmpty();
  }

  @Test
  void layerSwitchTurnsThatLayerOff() throws Exception {
    Svc svc = proxy(new Svc(new Dao()));
    props.setServices(false);

    svc.load();

    assertThat(seen).isEmpty();
  }

  @Test
  void markedClassUsesItsOwnLimit() {
    Job job = proxy(new Job());

    job.run();

    assertThat(seen).hasSize(1);
    assertThat(seen.get(0).layer()).isEqualTo(Layer.OTHER);
    assertThat(seen.get(0).wallLimitMs()).isEqualTo(5);
  }

  @Test
  void aFailingReporterDoesNotBreakTheCall() throws Exception {
    props.setEnabled(true);
    PerfReporter broken = r -> {
      throw new IllegalStateException("reporter down");
    };
    PerfMonitorAspect aspect =
        new PerfMonitorAspect(props, new CallTracker(props), List.of(broken));
    AspectJProxyFactory factory = new AspectJProxyFactory(new Svc(new Dao()));
    factory.setProxyTargetClass(true);
    factory.addAspect(aspect);
    Svc svc = factory.getProxy();

    assertThat(svc.load()).isEqualTo("row");
  }
}
