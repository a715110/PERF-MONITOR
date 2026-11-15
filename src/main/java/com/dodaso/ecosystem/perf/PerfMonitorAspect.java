package com.dodaso.ecosystem.perf;

import java.lang.reflect.Method;
import java.util.List;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Times controllers, services, repositories, JSF bean actions, calls to other services through
 * RESTServiceClient, and methods marked {@link Monitored}. Layers are found by stereotype, not by
 * package name. The monitoring never changes the result or the exception of a call, and a failure
 * inside the monitoring is swallowed.
 *
 * <p>Limits: calls made from inside the same class are not seen (Spring proxy AOP), and a method
 * that returns a future is timed only until it returns the future.
 */
@Aspect
public class PerfMonitorAspect {

  private final PerfProperties props;
  private final PerfRecorder recorder;

  public PerfMonitorAspect(PerfProperties props, PerfRecorder recorder) {
    this.props = props;
    this.recorder = recorder;
  }

  /** For tests and simple setups: builds its own recorder. */
  public PerfMonitorAspect(PerfProperties props, CallTracker tracker, List<PerfReporter> reporters) {
    this(props, new PerfRecorder(props, tracker, reporters));
  }

  @Pointcut("!within(com.dodaso.ecosystem.perf..*)")
  void notSelf() {
  }

  @Pointcut("@within(org.springframework.web.bind.annotation.RestController)"
      + " || @within(org.springframework.stereotype.Controller)")
  void controllers() {
  }

  @Pointcut("@within(org.springframework.stereotype.Service)")
  void services() {
  }

  @Pointcut("@within(org.springframework.stereotype.Repository)"
      + " || within(org.springframework.data.repository.Repository+)")
  void repositories() {
  }

  @Pointcut("@within(jakarta.inject.Named)")
  void jsfBeans() {
  }

  @Pointcut("execution(public * *(..)) && !execution(* get*(..)) && !execution(* is*(..))"
      + " && !execution(* set*(..))")
  void actionMethods() {
  }

  @Pointcut("execution(public * com.dodaso.ecosystem.baseline.common.proxy.RESTServiceClient.*(..))")
  void restClient() {
  }

  @Pointcut("@annotation(com.dodaso.ecosystem.perf.Monitored)"
      + " || @within(com.dodaso.ecosystem.perf.Monitored)")
  void marked() {
  }

  @Around("notSelf() && controllers()")
  public Object aroundController(ProceedingJoinPoint pjp) throws Throwable {
    return measure(pjp, Layer.CONTROLLER, props.isControllers(), null);
  }

  @Around("notSelf() && services() && !controllers()")
  public Object aroundService(ProceedingJoinPoint pjp) throws Throwable {
    return measure(pjp, Layer.SERVICE, props.isServices(), null);
  }

  @Around("notSelf() && repositories() && !controllers() && !services()")
  public Object aroundRepository(ProceedingJoinPoint pjp) throws Throwable {
    return measure(pjp, Layer.REPOSITORY, props.isRepositories(), null);
  }

  @Around("notSelf() && jsfBeans() && actionMethods() && !controllers() && !services()"
      + " && !repositories()")
  public Object aroundUiBean(ProceedingJoinPoint pjp) throws Throwable {
    return measure(pjp, Layer.UI, props.isUiBeans(), null);
  }

  @Around("notSelf() && restClient()")
  public Object aroundRestClient(ProceedingJoinPoint pjp) throws Throwable {
    return measure(pjp, Layer.REST_CLIENT, props.isRestClients(), describeRest(pjp.getArgs()));
  }

  @Around("notSelf() && marked() && !controllers() && !services() && !repositories()"
      + " && !jsfBeans() && !restClient()")
  public Object aroundMarked(ProceedingJoinPoint pjp) throws Throwable {
    return measure(pjp, Layer.OTHER, true, null);
  }

  private Object measure(ProceedingJoinPoint pjp, Layer layer, boolean layerOn, String detail)
      throws Throwable {
    if (!layerOn || !recorder.active()) {
      return pjp.proceed();
    }
    MethodSignature signature = (MethodSignature) pjp.getSignature();
    String method = detail == null ? signature.getName() : signature.getName() + " " + detail;
    CallTracker.Frame frame = recorder.begin(layer, signature.getDeclaringType().getSimpleName(),
        method, monitoredOn(signature.getMethod()));
    Throwable error = null;
    try {
      return pjp.proceed();
    } catch (Throwable t) {
      error = t;
      throw t;
    } finally {
      recorder.end(frame, error);
    }
  }

  /**
   * Names the target of a RESTServiceClient call: {@code service /path/{id}}. Understands the
   * (serviceName, endpoint, ...) form and RESTReqContainer. Returns null if it cannot tell.
   */
  static String describeRest(Object[] args) {
    try {
      if (args == null || args.length == 0) {
        return null;
      }
      if (args.length >= 2 && args[0] instanceof String service && args[1] instanceof String path) {
        return service + " " + PerfNames.path(path);
      }
      Object first = args[0];
      if (first != null) {
        Object service = first.getClass().getMethod("getServiceDiscoveryName").invoke(first);
        Object path = first.getClass().getMethod("getServiceCommandEndPoint").invoke(first);
        if (service != null && path != null) {
          return service + " " + PerfNames.path(String.valueOf(path));
        }
      }
    } catch (ReflectiveOperationException | RuntimeException e) {
      return null;
    }
    return null;
  }

  private static Monitored monitoredOn(Method method) {
    Monitored onMethod = AnnotatedElementUtils.findMergedAnnotation(method, Monitored.class);
    return onMethod != null ? onMethod
        : AnnotatedElementUtils.findMergedAnnotation(method.getDeclaringClass(), Monitored.class);
  }
}
