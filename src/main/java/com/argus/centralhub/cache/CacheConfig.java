package com.argus.centralhub.cache;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CacheConfig {

    @Value("${cache.local-cache.enabled:true}")
    private boolean useLocalCache;

    @Value("${cache.distributed.enabled:true}")
    private boolean useDistributedCache;

    @Value("${cache.ttl-minutes:3}")
    private int ttlMinutes;

    public boolean isUseLocalCache() {
        return useLocalCache;
    }

    public boolean isUseDistributedCache() {
        return useDistributedCache;
    }

    public int getTtlMinutes() {
        return ttlMinutes;
    }
}
