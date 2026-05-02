package com.argus.centralhub.ratelimit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RateLimitConfig {

    @Value("${rate-limit.local.enabled:true}")
    private boolean useLocalCache;

    @Value("${rate-limit.local.limit:100}")
    private int localLimit;

    @Value("${rate-limit.local.window-seconds:60}")
    private int localWindowSeconds;

    @Value("${rate-limit.distributed.enabled:true}")
    private boolean useDistributedLimit;

    @Value("${rate-limit.distributed.limit:500}")
    private int distributedLimit;

    @Value("${rate-limit.distributed.window-seconds:60}")
    private int distributedWindowSeconds;

    @Value("${rate-limit.max-wait-retries:3}")
    private int maxWaitRetries;

    @Value("${rate-limit.retry-interval-ms:500}")
    private long retryIntervalMs;

    public boolean isUseLocalCache() {
        return useLocalCache;
    }

    public int getLocalLimit() {
        return localLimit;
    }

    public int getLocalWindowSeconds() {
        return localWindowSeconds;
    }

    public boolean isUseDistributedLimit() {
        return useDistributedLimit;
    }

    public int getDistributedLimit() {
        return distributedLimit;
    }

    public int getDistributedWindowSeconds() {
        return distributedWindowSeconds;
    }

    public int getMaxWaitRetries() {
        return maxWaitRetries;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }
}
