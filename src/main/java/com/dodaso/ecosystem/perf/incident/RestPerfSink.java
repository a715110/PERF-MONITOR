package com.dodaso.ecosystem.perf.incident;

import com.dodaso.ecosystem.baseline.common.constant.ServiceDiscoveryEnum;
import com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient;
import java.util.List;
import org.springframework.core.ParameterizedTypeReference;

/** Sends batches to the common service: POST /api/v1/perf/incidents. */
public class RestPerfSink implements PerfIncidentSink {

  static final String ENDPOINT = "/api/v1/perf/incidents";

  private final RESTServiceClient client;

  public RestPerfSink(RESTServiceClient client) {
    this.client = client;
  }

  @Override
  public boolean send(List<PerfIncidentDto> batch) {
    try {
      client.post(ServiceDiscoveryEnum.common_service.getServiceDiscoveryName(), ENDPOINT,
          new PerfIncidentBatchDto(batch), new ParameterizedTypeReference<PerfIngestResultDto>() { });
      return true;
    } catch (Exception e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }
}
