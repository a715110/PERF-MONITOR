package com.dodaso.ecosystem.perf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Times every HTTP request as the outermost call, so a slow page or API call is seen as a whole.
 * Its own time (the time not spent in monitored controllers, beans, services or REST calls) is
 * mostly page rendering. Puts a request id (from the X-Request-Id header, or a new one) and the
 * signed-in user into the logging context for the length of the request.
 */
public class PerfRequestFilter extends OncePerRequestFilter {

  public static final String REQUEST_ID_HEADER = "X-Request-Id";

  private final PerfRecorder recorder;

  public PerfRequestFilter(PerfRecorder recorder) {
    this.recorder = recorder;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    PerfProperties props = recorder.properties();
    return !props.isEnabled() || !props.isRequests()
        || PerfNames.ignored(request.getRequestURI(), props.getIgnoredPaths());
  }

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain chain)
      throws ServletException, IOException {
    if (!recorder.active()) {
      chain.doFilter(request, response);
      return;
    }
    String incoming = request.getHeader(REQUEST_ID_HEADER);
    String requestId = incoming != null && !incoming.isBlank() && incoming.length() <= 64
        ? incoming : UUID.randomUUID().toString().substring(0, 8);
    boolean ownedId = MDC.get("requestId") == null;
    if (ownedId) {
      MDC.put("requestId", requestId);
    }
    if (request.getUserPrincipal() != null) {
      MDC.put("perfUser", request.getUserPrincipal().getName());
    }
    CallTracker.Frame frame = recorder.begin(Layer.REQUEST, "HTTP",
        request.getMethod() + " " + PerfNames.path(request.getRequestURI()), null);
    Throwable error = null;
    try {
      chain.doFilter(request, response);
    } catch (IOException | ServletException | RuntimeException e) {
      error = e;
      throw e;
    } finally {
      recorder.end(frame, error);
      MDC.remove("perfUser");
      if (ownedId) {
        MDC.remove("requestId");
      }
    }
  }
}
