package com.dodaso.ecosystem.perf;

import com.dodaso.ecosystem.perf.incident.IncidentPublisher;
import com.dodaso.ecosystem.perf.incident.PerfIncidentSink;
import com.dodaso.ecosystem.perf.incident.PerfIncidentWriter;
import com.dodaso.ecosystem.perf.incident.PersistingPerfReporter;
import com.dodaso.ecosystem.perf.incident.RestPerfSink;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Note: apps whose component scan covers com.dodaso.ecosystem (common-service, for one) also find
 * the nested configurations below on their own, without the outer class. That is why each nested
 * class repeats the dodaso.perf.enabled condition.
 *
 * Turns the monitor on when {@code dodaso.perf.enabled=true}. Add the jar to a service or a UI and
 * set the property; no other code change is needed.
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "dodaso.perf", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(PerfProperties.class)
public class PerfMonitorAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CallTracker perfCallTracker(PerfProperties props) {
    return new CallTracker(props);
  }

  @Bean
  public LoggingPerfReporter perfLoggingReporter(PerfProperties props) {
    return new LoggingPerfReporter(props);
  }

  @Bean
  @ConditionalOnMissingBean
  public PerfRecorder perfRecorder(
      PerfProperties props, CallTracker tracker, ObjectProvider<PerfReporter> reporters) {
    return new PerfRecorder(props, tracker, reporters.orderedStream().toList());
  }

  @Bean
  @ConditionalOnMissingBean
  public PerfMonitorAspect perfMonitorAspect(PerfProperties props, PerfRecorder recorder) {
    return new PerfMonitorAspect(props, recorder);
  }

  @Bean
  @ConditionalOnProperty(prefix = "dodaso.perf.watchdog", name = "enabled", matchIfMissing = true)
  @ConditionalOnMissingBean
  public PerfWatchdog perfWatchdog(PerfProperties props, CallTracker tracker,
      ObjectProvider<IncidentPublisher> publisher, Environment env) {
    PerfWatchdog watchdog = new PerfWatchdog(props, tracker);
    IncidentPublisher p = publisher.getIfAvailable();
    if (p != null) {
      watchdog.setPublisher(p, serviceName(props, env));
    }
    return watchdog;
  }

  static String serviceName(PerfProperties props, Environment env) {
    String configured = props.getStore().getServiceName();
    if (configured != null && !configured.isBlank()) {
      return configured;
    }
    return env.getProperty("spring.application.name", "unknown");
  }

  /** Times every HTTP request. Ordered after Spring Security so the user is known. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "dodaso.perf", name = "enabled", havingValue = "true")
  @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
  @ConditionalOnClass(Filter.class)
  static class RequestConfiguration {

    @Bean
    FilterRegistrationBean<PerfRequestFilter> perfRequestFilter(PerfRecorder recorder) {
      FilterRegistrationBean<PerfRequestFilter> registration =
          new FilterRegistrationBean<>(new PerfRequestFilter(recorder));
      registration.setOrder(10);
      return registration;
    }
  }

  /** Timers, only when Micrometer is present. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(prefix = "dodaso.perf", name = "enabled", havingValue = "true")
  @ConditionalOnExpression("${dodaso.perf.metrics-enabled:true}")
  @ConditionalOnClass(MeterRegistry.class)
  static class MetricsConfiguration {

    @Bean
    MicrometerPerfReporter perfMicrometerReporter(ObjectProvider<MeterRegistry> registry) {
      return new MicrometerPerfReporter(registry::getIfAvailable);
    }
  }

  /** Stores incidents through a sink. Off unless dodaso.perf.store.enabled=true. */
  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(name = {"dodaso.perf.enabled", "dodaso.perf.store.enabled"}, havingValue = "true")
  static class StoreConfiguration {

    @Bean
    @ConditionalOnMissingBean(PerfIncidentSink.class)
    @ConditionalOnClass(name = "com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient")
    PerfIncidentSink perfRestSink(
        ObjectProvider<com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient> client) {
      return new RestPerfSink(client.getObject());
    }

    @Bean
    @ConditionalOnMissingBean
    PerfIncidentWriter perfIncidentWriter(
        PerfProperties props, PerfIncidentSink sink, CallTracker tracker) {
      return new PerfIncidentWriter(props, sink, tracker);
    }

    @Bean
    PersistingPerfReporter perfPersistingReporter(
        PerfProperties props, PerfIncidentWriter writer, Environment env) {
      return new PersistingPerfReporter(props, writer, serviceName(props, env));
    }
  }
}
