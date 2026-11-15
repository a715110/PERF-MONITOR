package com.dodaso.ecosystem.perf.incident;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Answers of the admin queries, shared by common-service and the admin page. */
public final class PerfPageDtos {

  private PerfPageDtos() {
  }

  /** One page of incidents with the total that match the filter. */
  public static class IncidentPage implements Serializable {
    private List<PerfIncidentDto> items = new ArrayList<>();
    private long total;
    private int page;
    private int size;

    public List<PerfIncidentDto> getItems() {
      return items;
    }

    public void setItems(List<PerfIncidentDto> items) {
      this.items = items;
    }

    public long getTotal() {
      return total;
    }

    public void setTotal(long total) {
      this.total = total;
    }

    public int getPage() {
      return page;
    }

    public void setPage(int page) {
      this.page = page;
    }

    public int getSize() {
      return size;
    }

    public void setSize(int size) {
      this.size = size;
    }
  }

  /** One line of the "who uses the most CPU" summary. */
  public static class TopCall implements Serializable {
    private String className;
    private String methodName;
    private long incidents;
    private long totalOwnCpuMs;
    private long maxOwnWallMs;

    public String getClassName() {
      return className;
    }

    public void setClassName(String className) {
      this.className = className;
    }

    public String getMethodName() {
      return methodName;
    }

    public void setMethodName(String methodName) {
      this.methodName = methodName;
    }

    public long getIncidents() {
      return incidents;
    }

    public void setIncidents(long incidents) {
      this.incidents = incidents;
    }

    public long getTotalOwnCpuMs() {
      return totalOwnCpuMs;
    }

    public void setTotalOwnCpuMs(long totalOwnCpuMs) {
      this.totalOwnCpuMs = totalOwnCpuMs;
    }

    public long getMaxOwnWallMs() {
      return maxOwnWallMs;
    }

    public void setMaxOwnWallMs(long maxOwnWallMs) {
      this.maxOwnWallMs = maxOwnWallMs;
    }
  }
}
