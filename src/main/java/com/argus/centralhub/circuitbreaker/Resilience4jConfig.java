package com.argus.centralhub.circuitbreaker;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import io.github.resilience4j.timelimiter.TimeLimiterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

@Configuration
public class Resilience4jConfig {

    private static final Logger log = LoggerFactory.getLogger(Resilience4jConfig.class);

    @Value("${resilience4j.circuitbreaker.failure-rate-threshold:50}")
    private float failureRateThreshold;

    @Value("${resilience4j.circuitbreaker.slow-call-rate-threshold:100}")
    private float slowCallRateThreshold;

    @Value("${resilience4j.circuitbreaker.slow-call-duration-threshold:5s}")
    private String slowCallDurationThreshold;

    @Value("${resilience4j.circuitbreaker.wait-duration-in-open-state:30s}")
    private String waitDurationInOpenState;

    @Value("${resilience4j.circuitbreaker.permitted-number-of-calls-in-half-open-state:3}")
    private int permittedNumberOfCallsInHalfOpenState;

    @Value("${resilience4j.circuitbreaker.sliding-window-size:10}")
    private int slidingWindowSize;

    @Value("${resilience4j.circuitbreaker.minimum-number-of-calls:5}")
    private int minimumNumberOfCalls;

    @Value("${resilience4j.retry.max-attempts:3}")
    private int maxAttempts;

    @Value("${resilience4j.retry.wait-duration:1s}")
    private String retryWaitDuration;

    @Value("${resilience4j.timelimiter.timeout-duration:30s}")
    private String timeoutDuration;

    @Bean
    public CircuitBreakerRegistry circuitBreakerRegistry() {
        CircuitBreakerConfig defaultConfig = CircuitBreakerConfig.custom()
                .failureRateThreshold(failureRateThreshold)
                .slowCallRateThreshold(slowCallRateThreshold)
                .slowCallDurationThreshold(parseDuration(slowCallDurationThreshold))
                .waitDurationInOpenState(parseDuration(waitDurationInOpenState))
                .permittedNumberOfCallsInHalfOpenState(permittedNumberOfCallsInHalfOpenState)
                .slidingWindowSize(slidingWindowSize)
                .minimumNumberOfCalls(minimumNumberOfCalls)
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .enableAutomaticTransitionFromOpenToHalfOpen()
                .build();

        log.info("Resilience4j CircuitBreaker 默认配置已初始化: 失败率阈值={}%, 滑动窗口大小={}, 熔断等待时间={}",
                failureRateThreshold, slidingWindowSize, waitDurationInOpenState);

        return CircuitBreakerRegistry.of(defaultConfig);
    }

    @Bean
    public RetryRegistry retryRegistry() {
        RetryConfig defaultConfig = RetryConfig.custom()
                .maxAttempts(maxAttempts)
                .waitDuration(parseDuration(retryWaitDuration))
                .retryExceptions(
                        java.net.SocketTimeoutException.class,
                        java.net.ConnectException.class,
                        java.io.IOException.class,
                        org.springframework.web.client.ResourceAccessException.class
                )
                .failAfterMaxAttempts(true)
                .build();

        log.info("Resilience4j Retry 默认配置已初始化: 最大重试次数={}, 重试等待时间={}",
                maxAttempts, retryWaitDuration);

        return RetryRegistry.of(defaultConfig);
    }

    @Bean
    public TimeLimiterRegistry timeLimiterRegistry() {
        TimeLimiterConfig defaultConfig = TimeLimiterConfig.custom()
                .timeoutDuration(parseDuration(timeoutDuration))
                .cancelRunningFuture(true)
                .build();

        log.info("Resilience4j TimeLimiter 默认配置已初始化: 超时时间={}", timeoutDuration);

        return TimeLimiterRegistry.of(defaultConfig);
    }

    private Duration parseDuration(String durationStr) {
        String upper = durationStr.toUpperCase().trim();
        if (upper.endsWith("S")) {
            return Duration.ofSeconds(Long.parseLong(upper.replace("S", "")));
        } else if (upper.endsWith("MS")) {
            return Duration.ofMillis(Long.parseLong(upper.replace("MS", "")));
        } else if (upper.endsWith("M")) {
            return Duration.ofMinutes(Long.parseLong(upper.replace("M", "")));
        } else {
            try {
                return Duration.parse("PT" + upper);
            } catch (Exception e) {
                return Duration.ofSeconds(Long.parseLong(upper));
            }
        }
    }
}
