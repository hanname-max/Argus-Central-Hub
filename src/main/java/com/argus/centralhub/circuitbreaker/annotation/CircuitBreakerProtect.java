package com.argus.centralhub.circuitbreaker.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CircuitBreakerProtect {

    String name() default "";

    String fallbackMethod() default "";

    boolean enableCircuitBreaker() default true;

    boolean enableRetry() default true;

    boolean enableTimeLimiter() default true;

    long timeoutMs() default 30000;

    int maxRetries() default 3;

    Class<? extends Throwable>[] retryExceptions() default {
            java.net.SocketTimeoutException.class,
            java.net.ConnectException.class,
            org.springframework.web.client.ResourceAccessException.class,
            java.io.IOException.class
    };

    Class<? extends Throwable>[] recordExceptions() default {
            Exception.class
    };

    Class<? extends Throwable>[] ignoreExceptions() default {
            IllegalArgumentException.class,
            NullPointerException.class
    };
}
