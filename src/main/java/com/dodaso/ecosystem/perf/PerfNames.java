package com.dodaso.ecosystem.perf;

import java.util.regex.Pattern;

/** Turns request paths and endpoints into names that stay the same from one call to the next. */
public final class PerfNames {

  private static final Pattern ID = Pattern.compile("/(\\d+|[0-9a-fA-F]{8}-[0-9a-fA-F-]{27,})(?=/|$)");
  private static final Pattern SESSION = Pattern.compile(";jsessionid=[^/?]*", Pattern.CASE_INSENSITIVE);

  private PerfNames() {
  }

  /** Removes a session id and the query string, and replaces numeric or UUID segments with {id}. */
  public static String path(String raw) {
    if (raw == null || raw.isEmpty()) {
      return "/";
    }
    String p = SESSION.matcher(raw).replaceAll("");
    int q = p.indexOf('?');
    if (q >= 0) {
      p = p.substring(0, q);
    }
    return ID.matcher(p).replaceAll("/{id}");
  }

  /** True when the path starts with one of the ignored prefixes. */
  public static boolean ignored(String path, java.util.List<String> prefixes) {
    if (path == null) {
      return false;
    }
    for (String prefix : prefixes) {
      if (!prefix.isEmpty() && path.startsWith(prefix)) {
        return true;
      }
    }
    return false;
  }
}
