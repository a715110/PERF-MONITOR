package com.dodaso.ecosystem.perf.incident;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/** Request body of the ingest call: a list of incidents. */
public class PerfIncidentBatchDto implements Serializable {

  private List<PerfIncidentDto> incidents = new ArrayList<>();

  public PerfIncidentBatchDto() {
  }

  public PerfIncidentBatchDto(List<PerfIncidentDto> incidents) {
    this.incidents = incidents;
  }

  public List<PerfIncidentDto> getIncidents() {
    return incidents;
  }

  public void setIncidents(List<PerfIncidentDto> incidents) {
    this.incidents = incidents;
  }
}
