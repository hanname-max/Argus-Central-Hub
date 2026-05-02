package com.argus.centralhub.ratelimit;

import com.argus.centralhub.domain.enums.CloudProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final String LUA_SCRIPT = """
            local key = KEYS[1]
            local limit = tonumber(ARGV[1])
            local windowSeconds = tonumber(ARGV[2])
            
            local current = redis.call('GET', key)
            if current and tonumber(current) >= limit then
                return 0
            end
            
            local newCount = redis.call('INCR', key)
            if newCount == 1 then
                redis.call('EXPIRE', key, windowSeconds)
            end
            
            return newCount <= limit and 1 or 0
            """;

    private final StringRedisTemplate redisTemplate;
    private final RateLimitConfig config;
    private final ConcurrentHashMap<String, LocalRateLimiter> localLimiters = new ConcurrentHashMap<>();

    public RateLimiterService(StringRedisTemplate redisTemplate, RateLimitConfig config) {
        this.redisTemplate = redisTemplate;
        this.config = config;
    }

    public boolean tryAcquire(CloudProvider provider, String operation) {
        String key = buildKey(provider, operation);
        
        if (config.isUseLocalCache()) {
            LocalRateLimiter localLimiter = localLimiters.computeIfAbsent(key, 
                k -> new LocalRateLimiter(config.getLocalLimit(), config.getLocalWindowSeconds()));
            if (!localLimiter.tryAcquire()) {
                log.warn("本地限流触发: {} - {}", provider, operation);
                return false;
            }
        }

        if (config.isUseDistributedLimit() && redisTemplate != null) {
            return tryAcquireDistributed(key);
        }

        return true;
    }

    private boolean tryAcquireDistributed(String key) {
        try {
            RedisScript<Long> script = RedisScript.of(LUA_SCRIPT, Long.class);
            
            Long result = redisTemplate.execute(
                script,
                Collections.singletonList(key),
                String.valueOf(config.getDistributedLimit()),
                String.valueOf(config.getDistributedWindowSeconds())
            );

            boolean allowed = result != null && result > 0;
            
            if (!allowed) {
                log.warn("分布式限流触发: {}", key);
            }
            
            return allowed;
        } catch (Exception e) {
            log.error("分布式限流检查失败，降级到允许通过: {}", e.getMessage());
            return true;
        }
    }

    private String buildKey(CloudProvider provider, String operation) {
        return "ratelimit:" + provider.getCode() + ":" + operation;
    }

    public void waitForToken(CloudProvider provider, String operation) throws InterruptedException {
        int maxRetries = config.getMaxWaitRetries();
        long retryIntervalMs = config.getRetryIntervalMs();

        for (int i = 0; i < maxRetries; i++) {
            if (tryAcquire(provider, operation)) {
                return;
            }
            if (i < maxRetries - 1) {
                Thread.sleep(retryIntervalMs);
            }
        }

        throw new RateLimitExceededException(
            "API 限流超过最大等待次数: " + provider + " - " + operation);
    }

    public static class RateLimitExceededException extends RuntimeException {
        public RateLimitExceededException(String message) {
            super(message);
        }
    }

    private static class LocalRateLimiter {
        private final int limit;
        private final Duration window;
        private final AtomicInteger counter = new AtomicInteger(0);
        private final AtomicLong windowStart = new AtomicLong(System.currentTimeMillis());

        public LocalRateLimiter(int limit, int windowSeconds) {
            this.limit = limit;
            this.window = Duration.ofSeconds(windowSeconds);
        }

        public boolean tryAcquire() {
            long now = System.currentTimeMillis();
            long currentWindowStart = windowStart.get();

            if (now - currentWindowStart > window.toMillis()) {
                if (windowStart.compareAndSet(currentWindowStart, now)) {
                    counter.set(0);
                }
            }

            return counter.incrementAndGet() <= limit;
        }
    }
}
