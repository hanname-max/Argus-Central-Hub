package com.argus.centralhub.circuitbreaker;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CircuitBreakerConfig {

    @Value("${circuit-breaker.failure-threshold:5}")
    private int failureThreshold;

    @Value("${circuit-breaker.wait-duration-seconds:30}")
    private int waitDurationSeconds;

    @Value("${circuit-breaker.ring-buffer-size:3}")
    private int ringBufferSize;

    public int getFailureThreshold() {
        return failureThreshold;
    }

    public int getWaitDurationSeconds() {
        return waitDurationSeconds;
    }

    public int getRingBufferSize() {
        return ringBufferSize;
    }
}
