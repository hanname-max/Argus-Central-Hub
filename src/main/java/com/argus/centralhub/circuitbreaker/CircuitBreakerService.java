package com.argus.centralhub.circuitbreaker;

import com.argus.centralhub.domain.enums.CloudProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CircuitBreakerService {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerService.class);

    private final ConcurrentHashMap<String, CircuitBreakerState> circuits = new ConcurrentHashMap<>();
    private final CircuitBreakerConfig config;

    public CircuitBreakerService(CircuitBreakerConfig config) {
        this.config = config;
    }

    public boolean allowRequest(CloudProvider provider, String operation) {
        String key = buildKey(provider, operation);
        CircuitBreakerState state = getCircuitBreaker(key);
        
        return state.allowRequest();
    }

    public void recordSuccess(CloudProvider provider, String operation) {
        String key = buildKey(provider, operation);
        CircuitBreakerState state = circuits.get(key);
        if (state != null) {
            state.recordSuccess();
        }
    }

    public void recordFailure(CloudProvider provider, String operation) {
        String key = buildKey(provider, operation);
        CircuitBreakerState state = circuits.get(key);
        if (state != null) {
            state.recordFailure();
        }
    }

    public CircuitBreakerState getCircuitBreaker(String key) {
        return circuits.computeIfAbsent(key, k -> 
            new CircuitBreakerState(
                config.getFailureThreshold(),
                config.getWaitDurationSeconds(),
                config.getRingBufferSize()
            )
        );
    }

    private String buildKey(CloudProvider provider, String operation) {
        return provider.getCode() + ":" + operation;
    }

    public void resetAll() {
        circuits.clear();
        log.info("所有熔断器已重置");
    }

    public static class CircuitBreakerState {
        private final AtomicInteger failureCount = new AtomicInteger(0);
        private final AtomicInteger successCount = new AtomicInteger(0);
        private final AtomicLong lastFailureTime = new AtomicLong(0);
        private final AtomicLong openUntil = new AtomicLong(0);
        private final int failureThreshold;
        private final long waitDurationMs;
        private final int ringBufferSize;

        public CircuitBreakerState(int failureThreshold, int waitDurationSeconds, int ringBufferSize) {
            this.failureThreshold = failureThreshold;
            this.waitDurationMs = Duration.ofSeconds(waitDurationSeconds).toMillis();
            this.ringBufferSize = ringBufferSize;
        }

        public boolean allowRequest() {
            long now = System.currentTimeMillis();
            long openTime = openUntil.get();

            if (openTime > 0) {
                if (now < openTime) {
                    log.debug("熔断器处于 OPEN 状态，拒绝请求");
                    return false;
                } else {
                    if (openUntil.compareAndSet(openTime, 0)) {
                        log.info("熔断器从 OPEN 进入 HALF-OPEN 状态");
                        failureCount.set(0);
                        successCount.set(0);
                    }
                    return true;
                }
            }

            return true;
        }

        public void recordSuccess() {
            long currentOpenUntil = openUntil.get();
            
            if (currentOpenUntil > 0 && System.currentTimeMillis() > currentOpenUntil) {
                int successes = successCount.incrementAndGet();
                if (successes >= ringBufferSize) {
                    if (openUntil.compareAndSet(currentOpenUntil, 0)) {
                        log.info("HALF-OPEN 状态下成功次数足够，熔断器进入 CLOSED 状态");
                        failureCount.set(0);
                        successCount.set(0);
                    }
                }
            } else {
                failureCount.set(0);
            }
        }

        public void recordFailure() {
            long now = System.currentTimeMillis();
            lastFailureTime.set(now);
            
            int failures = failureCount.incrementAndGet();
            
            if (failures >= failureThreshold && openUntil.get() == 0) {
                long newOpenUntil = now + waitDurationMs;
                if (openUntil.compareAndSet(0, newOpenUntil)) {
                    log.warn("熔断器打开！失败次数: {}, 将在 {} 毫秒后尝试恢复", 
                        failures, waitDurationMs);
                }
            }
        }

        public int getFailureCount() {
            return failureCount.get();
        }

        public boolean isOpen() {
            long openTime = openUntil.get();
            return openTime > 0 && System.currentTimeMillis() < openTime;
        }

        public boolean isHalfOpen() {
            long openTime = openUntil.get();
            return openTime > 0 && System.currentTimeMillis() >= openTime;
        }

        public boolean isClosed() {
            return !isOpen() && !isHalfOpen();
        }
    }

    public enum CircuitBreakerStatus {
        CLOSED,
        OPEN,
        HALF_OPEN
    }
}
