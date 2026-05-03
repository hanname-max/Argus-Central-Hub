package com.argus.centralhub.circuitbreaker;

import com.argus.centralhub.circuitbreaker.annotation.CircuitBreakerProtect;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Supplier;

@Slf4j
@Aspect
@Component
public class CircuitBreakerAspect {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final TimeLimiterRegistry timeLimiterRegistry;
    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Retry> retryCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, TimeLimiter> timeLimiterCache = new ConcurrentHashMap<>();
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    public CircuitBreakerAspect(CircuitBreakerRegistry circuitBreakerRegistry,
                                 RetryRegistry retryRegistry,
                                 TimeLimiterRegistry timeLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.timeLimiterRegistry = timeLimiterRegistry;
    }

    @Around("@annotation(annotation)")
    public Object around(ProceedingJoinPoint point, CircuitBreakerProtect annotation) throws Throwable {
        String name = resolveName(point, annotation);
        MethodSignature signature = (MethodSignature) point.getSignature();
        Method method = signature.getMethod();
        Object[] args = point.getArgs();
        Object target = point.getTarget();

        log.debug("进入断路器保护: {} - 方法: {}", name, method.getName());

        Supplier<Object> supplier = () -> {
            try {
                return point.proceed();
            } catch (Throwable throwable) {
                if (throwable instanceof RuntimeException) {
                    throw (RuntimeException) throwable;
                }
                throw new CompletionException(throwable);
            }
        };

        if (annotation.enableTimeLimiter()) {
            TimeLimiter timeLimiter = getOrCreateTimeLimiter(name, annotation);
            supplier = decorateWithTimeLimiter(supplier, timeLimiter, name);
        }

        if (annotation.enableRetry()) {
            Retry retry = getOrCreateRetry(name, annotation);
            supplier = decorateWithRetry(supplier, retry, name);
        }

        if (annotation.enableCircuitBreaker()) {
            CircuitBreaker circuitBreaker = getOrCreateCircuitBreaker(name, annotation);
            supplier = decorateWithCircuitBreaker(supplier, circuitBreaker, name);
        }

        try {
            Object result = supplier.get();
            log.debug("断路器保护执行成功: {}", name);
            return result;
        } catch (Exception e) {
            log.warn("断路器保护执行异常: {} - {}", name, e.getMessage());
            
            if (annotation.fallbackMethod() != null && !annotation.fallbackMethod().isEmpty()) {
                return invokeFallback(target, annotation.fallbackMethod(), args, e);
            }
            
            Throwable cause = e instanceof CompletionException ? e.getCause() : e;
            if (cause != null) {
                throw cause;
            }
            throw e;
        }
    }

    private String resolveName(ProceedingJoinPoint point, CircuitBreakerProtect annotation) {
        if (annotation.name() != null && !annotation.name().isEmpty()) {
            return annotation.name();
        }
        MethodSignature signature = (MethodSignature) point.getSignature();
        return signature.getDeclaringType().getSimpleName() + "." + signature.getName();
    }

    private CircuitBreaker getOrCreateCircuitBreaker(String name, CircuitBreakerProtect annotation) {
        return circuitBreakerCache.computeIfAbsent(name, k -> {
            Set<Class<? extends Throwable>> recordExceptions = new HashSet<>(Arrays.asList(annotation.recordExceptions()));
            Set<Class<? extends Throwable>> ignoreExceptions = new HashSet<>(Arrays.asList(annotation.ignoreExceptions()));

            CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                    .failureRateThreshold(50)
                    .slowCallRateThreshold(100)
                    .slowCallDurationThreshold(Duration.ofSeconds(5))
                    .waitDurationInOpenState(Duration.ofSeconds(30))
                    .permittedNumberOfCallsInHalfOpenState(3)
                    .slidingWindowSize(10)
                    .minimumNumberOfCalls(5)
                    .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                    .enableAutomaticTransitionFromOpenToHalfOpen()
                    .recordExceptions(recordExceptions.toArray(new Class[0]))
                    .ignoreExceptions(ignoreExceptions.toArray(new Class[0]))
                    .build();

            CircuitBreaker circuitBreaker = circuitBreakerRegistry.circuitBreaker(name, config);
            
            circuitBreaker.getEventPublisher()
                    .onStateTransition(event -> 
                        log.warn("断路器 [{}] 状态变化: {} -> {}", 
                                name, event.getStateTransition().getFromState(), 
                                event.getStateTransition().getToState()))
                    .onError(event -> 
                        log.debug("断路器 [{}] 记录错误: {}", name, event.getThrowable().getMessage()))
                    .onSuccess(event -> 
                        log.trace("断路器 [{}] 记录成功", name))
                    .onCallNotPermitted(event -> 
                        log.warn("断路器 [{}] 处于 OPEN 状态，快速失败", name));

            log.info("创建断路器实例: {}", name);
            return circuitBreaker;
        });
    }

    private Retry getOrCreateRetry(String name, CircuitBreakerProtect annotation) {
        return retryCache.computeIfAbsent(name, k -> {
            Set<Class<? extends Throwable>> retryExceptions = new HashSet<>(Arrays.asList(annotation.retryExceptions()));

            RetryConfig config = RetryConfig.custom()
                    .maxAttempts(annotation.maxRetries())
                    .waitDuration(Duration.ofSeconds(1))
                    .retryExceptions(retryExceptions.toArray(new Class[0]))
                    .failAfterMaxAttempts(true)
                    .build();

            Retry retry = retryRegistry.retry(name, config);
            
            retry.getEventPublisher()
                    .onRetry(event -> 
                        log.warn("重试 [{}] 第 {} 次尝试，原因: {}", 
                                name, event.getNumberOfRetryAttempts(), 
                                event.getLastThrowable().getMessage()))
                    .onError(event -> 
                        log.warn("重试 [{}] 最终失败，已达最大重试次数: {}", 
                                name, event.getThrowable().getMessage()));

            log.info("创建重试实例: {} (最大重试次数: {})", name, annotation.maxRetries());
            return retry;
        });
    }

    private TimeLimiter getOrCreateTimeLimiter(String name, CircuitBreakerProtect annotation) {
        return timeLimiterCache.computeIfAbsent(name, k -> {
            TimeLimiterConfig config = TimeLimiterConfig.custom()
                    .timeoutDuration(Duration.ofMillis(annotation.timeoutMs()))
                    .cancelRunningFuture(true)
                    .build();

            TimeLimiter timeLimiter = timeLimiterRegistry.timeLimiter(name, config);
            
            timeLimiter.getEventPublisher()
                    .onTimeout(event -> 
                            log.warn("时间限制器 [{}] 超时 ({}ms)", name, annotation.timeoutMs()));

            log.info("创建时间限制器实例: {} (超时: {}ms)", name, annotation.timeoutMs());
            return timeLimiter;
        });
    }

    private Supplier<Object> decorateWithCircuitBreaker(Supplier<Object> supplier, CircuitBreaker circuitBreaker, String name) {
        return CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
    }

    private Supplier<Object> decorateWithRetry(Supplier<Object> supplier, Retry retry, String name) {
        return Retry.decorateSupplier(retry, supplier);
    }

    private Supplier<Object> decorateWithTimeLimiter(Supplier<Object> supplier, TimeLimiter timeLimiter, String name) {
        return () -> {
            CompletableFuture<Object> future = CompletableFuture.supplyAsync(supplier, virtualThreadExecutor);
            try {
                return timeLimiter.executeFutureSupplier(() -> future);
            } catch (Exception e) {
                future.cancel(false);
                throw e;
            }
        };
    }

    private Object invokeFallback(Object target, String fallbackMethodName, Object[] args, Throwable cause) throws Throwable {
        Class<?> targetClass = target.getClass();
        Method fallbackMethod = null;
        boolean useCause = false;

        try {
            Class<?>[] paramTypes = new Class[args.length + 1];
            for (int i = 0; i < args.length; i++) {
                paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
            }
            paramTypes[args.length] = Throwable.class;
            
            fallbackMethod = findMethod(targetClass, fallbackMethodName, paramTypes);
            useCause = true;
        } catch (NoSuchMethodException e) {
            try {
                Class<?>[] paramTypes = new Class[args.length];
                for (int i = 0; i < args.length; i++) {
                    paramTypes[i] = args[i] != null ? args[i].getClass() : Object.class;
                }
                fallbackMethod = findMethod(targetClass, fallbackMethodName, paramTypes);
                useCause = false;
            } catch (NoSuchMethodException ex) {
                log.warn("未找到 fallback 方法: {}.{}", targetClass.getSimpleName(), fallbackMethodName);
                throw cause;
            }
        }

        try {
            fallbackMethod.setAccessible(true);
            Object result;
            if (useCause) {
                Object[] fallbackArgs = new Object[args.length + 1];
                System.arraycopy(args, 0, fallbackArgs, 0, args.length);
                fallbackArgs[args.length] = cause;
                result = fallbackMethod.invoke(target, fallbackArgs);
            } else {
                result = fallbackMethod.invoke(target, args);
            }

            log.info("执行 fallback 方法: {}.{}", targetClass.getSimpleName(), fallbackMethodName);
            return result;

        } catch (InvocationTargetException e) {
            Throwable targetException = e.getTargetException();
            log.error("Fallback 方法执行失败: {}", targetException.getMessage());
            throw targetException;
        }
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?>[] paramTypes) throws NoSuchMethodException {
        Method[] methods = clazz.getDeclaredMethods();
        for (Method method : methods) {
            if (method.getName().equals(methodName)) {
                Class<?>[] methodParams = method.getParameterTypes();
                if (methodParams.length == paramTypes.length) {
                    boolean match = true;
                    for (int i = 0; i < methodParams.length; i++) {
                        if (!methodParams[i].isAssignableFrom(paramTypes[i])) {
                            match = false;
                            break;
                        }
                    }
                    if (match) {
                        return method;
                    }
                }
            }
        }
        throw new NoSuchMethodException(methodName);
    }
}
