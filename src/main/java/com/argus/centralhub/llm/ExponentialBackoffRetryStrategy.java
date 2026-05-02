package com.argus.centralhub.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ThreadLocalRandom;

public class ExponentialBackoffRetryStrategy {

    private static final Logger log = LoggerFactory.getLogger(ExponentialBackoffRetryStrategy.class);

    private final int maxAttempts;
    private final long initialDelayMs;
    private final double multiplier;
    private final long maxDelayMs;

    public ExponentialBackoffRetryStrategy(int maxAttempts, long initialDelayMs, double multiplier, long maxDelayMs) {
        this.maxAttempts = maxAttempts;
        this.initialDelayMs = initialDelayMs;
        this.multiplier = multiplier;
        this.maxDelayMs = maxDelayMs;
    }

    public boolean shouldRetry(int attempt, LlmException exception) {
        if (attempt >= maxAttempts) {
            log.debug("已达到最大重试次数 {}，不再重试", maxAttempts);
            return false;
        }
        if (!exception.isRetryable()) {
            log.debug("错误类型 {} 不可重试", exception.getErrorType());
            return false;
        }
        return true;
    }

    public void backoff(int attempt) throws InterruptedException {
        long delayMs = calculateDelay(attempt);
        long jitteredDelay = addJitter(delayMs);
        
        log.debug("第 {} 次重试前等待 {} 毫秒", attempt + 1, jitteredDelay);
        
        Thread.sleep(jitteredDelay);
    }

    public long calculateDelay(int attempt) {
        double delay = initialDelayMs * Math.pow(multiplier, attempt);
        return Math.min((long) Math.ceil(delay), maxDelayMs);
    }

    private long addJitter(long delayMs) {
        if (delayMs <= 0) {
            return 0;
        }
        long jitter = ThreadLocalRandom.current().nextLong(0, delayMs / 2 + 1);
        return delayMs + jitter;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private int maxAttempts = 3;
        private long initialDelayMs = 1000;
        private double multiplier = 2.0;
        private long maxDelayMs = 10000;

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder initialDelayMs(long initialDelayMs) {
            this.initialDelayMs = initialDelayMs;
            return this;
        }

        public Builder multiplier(double multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        public Builder maxDelayMs(long maxDelayMs) {
            this.maxDelayMs = maxDelayMs;
            return this;
        }

        public ExponentialBackoffRetryStrategy build() {
            return new ExponentialBackoffRetryStrategy(maxAttempts, initialDelayMs, multiplier, maxDelayMs);
        }
    }
}
