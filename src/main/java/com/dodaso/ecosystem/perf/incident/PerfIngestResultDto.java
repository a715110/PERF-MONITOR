package com.dodaso.ecosystem.perf.incident;

import java.io.Serializable;

/** Answer of the ingest call: how many incidents were stored. */
public class PerfIngestResultDto implements Serializable {

  private int accepted;

  public PerfIngestResultDto() {
  }

  public PerfIngestResultDto(int accepted) {
    this.accepted = accepted;
  }

  public int getAccepted() {
    return accepted;
  }

  public void setAccepted(int accepted) {
    this.accepted = accepted;
  }
}
