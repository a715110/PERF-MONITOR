package com.dodaso.ecosystem.perf;

/** The kind of code a monitored call belongs to. */
public enum Layer {
  /** A whole HTTP request, from the servlet filter. Its own time is mostly page rendering. */
  REQUEST,
  CONTROLLER,
  SERVICE,
  REPOSITORY,
  /** A public action method of a JSF bean (a button, a listener, a page load). */
  UI,
  /** A call from this app to another service through RESTServiceClient. */
  REST_CLIENT,
  /** A method marked with {@link Monitored} outside the layers above. */
  OTHER
}
