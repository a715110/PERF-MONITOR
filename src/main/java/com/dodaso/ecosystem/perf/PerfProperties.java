package com.dodaso.ecosystem.perf;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings under {@code dodaso.perf}. Everything is off until {@code dodaso.perf.enabled=true}.
 * "Own time" of a call is its time minus the time of the monitored calls it made, so a slow
 * repository call is blamed on the repository, not on the service and controller above it.
 */
@ConfigurationProperties(prefix = "dodaso.perf")
public class PerfProperties {

  /** Master switch. */
  private boolean enabled = false;

  /** Own wall-clock time (ms) at which a controller or service call is reported. */
  private long wallThresholdMs = 3000;

  /** Own wall-clock time (ms) at which a repository call is reported. */
  private long repositoryWallThresholdMs = 1000;

  /** Own CPU time (ms) at which any call is reported. */
  private long cpuThresholdMs = 1000;

  /** Total time (ms) at which an outermost call (a whole request) is reported. */
  private long entryThresholdMs = 5000;

  /** Record Micrometer timers when a MeterRegistry is present. */
  private boolean metricsEnabled = true;

  /** Own wall-clock time (ms) at which a call to another service is reported. */
  private long restWallThresholdMs = 2000;

  /** Watch JSF bean actions, calls to other services, and whole requests. */
  private boolean uiBeans = true;
  private boolean restClients = true;
  private boolean requests = true;

  /** Request paths that are never timed (prefix match), such as static resources. */
  private List<String> ignoredPaths = new ArrayList<>(List.of(
      "/actuator", "/javax.faces.resource", "/jakarta.faces.resource", "/resources", "/favicon",
      "/error"));

  /** Layers to watch. */
  private boolean controllers = true;
  private boolean services = true;
  private boolean repositories = true;

  /** Per class or per class.method own-time limits in ms, for example {@code ContractService.save}. */
  private Map<String, Long> overrides = new LinkedHashMap<>();

  private final Watchdog watchdog = new Watchdog();

  private final Store store = new Store();

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public long getWallThresholdMs() {
    return wallThresholdMs;
  }

  public void setWallThresholdMs(long wallThresholdMs) {
    this.wallThresholdMs = wallThresholdMs;
  }

  public long getRepositoryWallThresholdMs() {
    return repositoryWallThresholdMs;
  }

  public void setRepositoryWallThresholdMs(long repositoryWallThresholdMs) {
    this.repositoryWallThresholdMs = repositoryWallThresholdMs;
  }

  public long getCpuThresholdMs() {
    return cpuThresholdMs;
  }

  public void setCpuThresholdMs(long cpuThresholdMs) {
    this.cpuThresholdMs = cpuThresholdMs;
  }

  public long getEntryThresholdMs() {
    return entryThresholdMs;
  }

  public void setEntryThresholdMs(long entryThresholdMs) {
    this.entryThresholdMs = entryThresholdMs;
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public void setMetricsEnabled(boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
  }

  public boolean isControllers() {
    return controllers;
  }

  public void setControllers(boolean controllers) {
    this.controllers = controllers;
  }

  public boolean isServices() {
    return services;
  }

  public void setServices(boolean services) {
    this.services = services;
  }

  public boolean isRepositories() {
    return repositories;
  }

  public void setRepositories(boolean repositories) {
    this.repositories = repositories;
  }

  public Map<String, Long> getOverrides() {
    return overrides;
  }

  public void setOverrides(Map<String, Long> overrides) {
    this.overrides = overrides;
  }

  public Watchdog getWatchdog() {
    return watchdog;
  }

  public Store getStore() {
    return store;
  }

  public long getRestWallThresholdMs() {
    return restWallThresholdMs;
  }

  public void setRestWallThresholdMs(long restWallThresholdMs) {
    this.restWallThresholdMs = restWallThresholdMs;
  }

  public boolean isUiBeans() {
    return uiBeans;
  }

  public void setUiBeans(boolean uiBeans) {
    this.uiBeans = uiBeans;
  }

  public boolean isRestClients() {
    return restClients;
  }

  public void setRestClients(boolean restClients) {
    this.restClients = restClients;
  }

  public boolean isRequests() {
    return requests;
  }

  public void setRequests(boolean requests) {
    this.requests = requests;
  }

  public List<String> getIgnoredPaths() {
    return ignoredPaths;
  }

  public void setIgnoredPaths(List<String> ignoredPaths) {
    this.ignoredPaths = ignoredPaths;
  }

  /** Own-time limit for one call: annotation, then class.method, then class, then the layer. */
  public long wallLimitFor(Layer layer, String className, String methodName, Monitored monitored) {
    if (monitored != null && monitored.wallMs() >= 0) {
      return monitored.wallMs();
    }
    Long exact = overrides.get(className + "." + methodName);
    if (exact != null) {
      return exact;
    }
    Long perClass = overrides.get(className);
    if (perClass != null) {
      return perClass;
    }
    return switch (layer) {
      case REPOSITORY -> repositoryWallThresholdMs;
      case REST_CLIENT -> restWallThresholdMs;
      default -> wallThresholdMs;
    };
  }

  /** Own CPU limit for one call: annotation, then the default. */
  public long cpuLimitFor(Monitored monitored) {
    return monitored != null && monitored.cpuMs() >= 0 ? monitored.cpuMs() : cpuThresholdMs;
  }

  /** Settings of the incident store under {@code dodaso.perf.store}. */
  public static class Store {

    /** Send incidents to the common service for the admin page. Needs RESTServiceClient. */
    private boolean enabled = false;

    /** Name written on each incident; empty means spring.application.name. */
    private String serviceName = "";

    /** Most incidents waiting to be sent. Newer ones are dropped when the queue is full. */
    private int queueSize = 2000;

    /** Most incidents sent in one call. */
    private int batchSize = 50;

    /** The writer sends what it has at least this often, in ms. */
    private long flushIntervalMs = 5000;

    /** Keep the stack text of STILL_RUNNING and HIGH_CPU incidents. */
    private boolean includeStack = true;

    /** Longest stack text kept, in characters. */
    private int maxStackChars = 6000;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public String getServiceName() {
      return serviceName;
    }

    public void setServiceName(String serviceName) {
      this.serviceName = serviceName;
    }

    public int getQueueSize() {
      return queueSize;
    }

    public void setQueueSize(int queueSize) {
      this.queueSize = queueSize;
    }

    public int getBatchSize() {
      return batchSize;
    }

    public void setBatchSize(int batchSize) {
      this.batchSize = batchSize;
    }

    public long getFlushIntervalMs() {
      return flushIntervalMs;
    }

    public void setFlushIntervalMs(long flushIntervalMs) {
      this.flushIntervalMs = flushIntervalMs;
    }

    public boolean isIncludeStack() {
      return includeStack;
    }

    public void setIncludeStack(boolean includeStack) {
      this.includeStack = includeStack;
    }

    public int getMaxStackChars() {
      return maxStackChars;
    }

    public void setMaxStackChars(int maxStackChars) {
      this.maxStackChars = maxStackChars;
    }
  }

  /** Settings of the background watchdog under {@code dodaso.perf.watchdog}. */
  public static class Watchdog {

    /** Watch calls that are still running, and busy threads when the process CPU is high. */
    private boolean enabled = true;

    /** How often the watchdog looks, in ms. */
    private long intervalMs = 2000;

    /** A monitored call still running after this many ms is reported with its stack. */
    private long stuckThresholdMs = 10000;

    /** The same running call is reported again only after this many ms. */
    private long repeatMs = 30000;

    /** Process CPU load (0 to 1) at or above which the busiest threads are reported. */
    private double cpuLoadThreshold = 0.85;

    /** Busy thread reports are at least this many ms apart. */
    private long cpuReportEveryMs = 30000;

    /** Number of busiest threads in one report. */
    private int topThreads = 5;

    /** Stack frames shown per thread. */
    private int stackDepth = 15;

    public boolean isEnabled() {
      return enabled;
    }

    public void setEnabled(boolean enabled) {
      this.enabled = enabled;
    }

    public long getIntervalMs() {
      return intervalMs;
    }

    public void setIntervalMs(long intervalMs) {
      this.intervalMs = intervalMs;
    }

    public long getStuckThresholdMs() {
      return stuckThresholdMs;
    }

    public void setStuckThresholdMs(long stuckThresholdMs) {
      this.stuckThresholdMs = stuckThresholdMs;
    }

    public long getRepeatMs() {
      return repeatMs;
    }

    public void setRepeatMs(long repeatMs) {
      this.repeatMs = repeatMs;
    }

    public double getCpuLoadThreshold() {
      return cpuLoadThreshold;
    }

    public void setCpuLoadThreshold(double cpuLoadThreshold) {
      this.cpuLoadThreshold = cpuLoadThreshold;
    }

    public long getCpuReportEveryMs() {
      return cpuReportEveryMs;
    }

    public void setCpuReportEveryMs(long cpuReportEveryMs) {
      this.cpuReportEveryMs = cpuReportEveryMs;
    }

    public int getTopThreads() {
      return topThreads;
    }

    public void setTopThreads(int topThreads) {
      this.topThreads = topThreads;
    }

    public int getStackDepth() {
      return stackDepth;
    }

    public void setStackDepth(int stackDepth) {
      this.stackDepth = stackDepth;
    }
  }
}
