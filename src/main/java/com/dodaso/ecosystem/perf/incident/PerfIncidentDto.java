package com.dodaso.ecosystem.perf.incident;

import com.dodaso.ecosystem.perf.CallResult;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * One incident as it travels from a service to the common service and back to the admin page.
 * Plain bean so it works with any Jackson setup. Times in milliseconds, occurredAt in UTC.
 */
public class PerfIncidentDto implements Serializable {

  private Long id;
  private LocalDateTime occurredAt;
  private String service;
  private String reason;
  private String layer;
  private String className;
  private String methodName;
  private String kind;
  private Long ownWallMs;
  private Long ownCpuMs;
  private Long wallMs;
  private Long cpuMs;
  private String outcome;
  private String threadName;
  private String requestId;
  private String userLogin;
  private String callChain;
  private String stackText;

  public PerfIncidentDto() {
  }

  /** Builds the incident of a finished call. */
  public static PerfIncidentDto from(CallResult r, String reason, String service) {
    PerfIncidentDto d = new PerfIncidentDto();
    d.occurredAt = LocalDateTime.now(ZoneOffset.UTC);
    d.service = service;
    d.reason = reason;
    d.layer = r.layer().name();
    d.className = r.className();
    d.methodName = r.methodName();
    d.kind = r.kind();
    d.ownWallMs = r.ownWallMs();
    d.ownCpuMs = r.ownCpuMs() < 0 ? null : r.ownCpuMs();
    d.wallMs = r.wallMs();
    d.cpuMs = r.cpuMs() < 0 ? null : r.cpuMs();
    d.outcome = r.outcome();
    d.threadName = r.thread();
    d.requestId = r.requestId();
    d.userLogin = r.user();
    d.callChain = r.chain();
    return d;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public LocalDateTime getOccurredAt() {
    return occurredAt;
  }

  public void setOccurredAt(LocalDateTime occurredAt) {
    this.occurredAt = occurredAt;
  }

  public String getService() {
    return service;
  }

  public void setService(String service) {
    this.service = service;
  }

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  public String getLayer() {
    return layer;
  }

  public void setLayer(String layer) {
    this.layer = layer;
  }

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

  public String getKind() {
    return kind;
  }

  public void setKind(String kind) {
    this.kind = kind;
  }

  public Long getOwnWallMs() {
    return ownWallMs;
  }

  public void setOwnWallMs(Long ownWallMs) {
    this.ownWallMs = ownWallMs;
  }

  public Long getOwnCpuMs() {
    return ownCpuMs;
  }

  public void setOwnCpuMs(Long ownCpuMs) {
    this.ownCpuMs = ownCpuMs;
  }

  public Long getWallMs() {
    return wallMs;
  }

  public void setWallMs(Long wallMs) {
    this.wallMs = wallMs;
  }

  public Long getCpuMs() {
    return cpuMs;
  }

  public void setCpuMs(Long cpuMs) {
    this.cpuMs = cpuMs;
  }

  public String getOutcome() {
    return outcome;
  }

  public void setOutcome(String outcome) {
    this.outcome = outcome;
  }

  public String getThreadName() {
    return threadName;
  }

  public void setThreadName(String threadName) {
    this.threadName = threadName;
  }

  public String getRequestId() {
    return requestId;
  }

  public void setRequestId(String requestId) {
    this.requestId = requestId;
  }

  public String getUserLogin() {
    return userLogin;
  }

  public void setUserLogin(String userLogin) {
    this.userLogin = userLogin;
  }

  public String getCallChain() {
    return callChain;
  }

  public void setCallChain(String callChain) {
    this.callChain = callChain;
  }

  public String getStackText() {
    return stackText;
  }

  public void setStackText(String stackText) {
    this.stackText = stackText;
  }

  /** The call as one name, for lists: Class.method. */
  public String callName() {
    return className + "." + methodName;
  }
}
