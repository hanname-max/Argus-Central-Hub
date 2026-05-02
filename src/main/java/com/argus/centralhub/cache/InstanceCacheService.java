package com.argus.centralhub.cache;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.model.CloudInstance;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
public class InstanceCacheService {

    private static final Logger log = LoggerFactory.getLogger(InstanceCacheService.class);
    private static final String CACHE_PREFIX = "instance:list:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheConfig cacheConfig;
    private final ConcurrentHashMap<String, CacheEntry<List<CloudInstance>>> localCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanUpExecutor = Executors.newSingleThreadScheduledExecutor();

    public InstanceCacheService(StringRedisTemplate redisTemplate, ObjectMapper objectMapper, CacheConfig cacheConfig) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.cacheConfig = cacheConfig;
        scheduleCleanup();
    }

    private void scheduleCleanup() {
        cleanUpExecutor.scheduleAtFixedRate(
            this::cleanupLocalCache,
            1, 1, TimeUnit.MINUTES
        );
    }

    private void cleanupLocalCache() {
        long now = System.currentTimeMillis();
        localCache.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
    }

    public Optional<List<CloudInstance>> getFromCache(CloudProvider provider, String region) {
        String key = buildCacheKey(provider, region);

        if (cacheConfig.isUseLocalCache()) {
            CacheEntry<List<CloudInstance>> localEntry = localCache.get(key);
            if (localEntry != null && !localEntry.isExpired()) {
                log.debug("本地缓存命中: {} - {}", provider, region);
                return Optional.of(localEntry.getValue());
            }
        }

        if (cacheConfig.isUseDistributedCache() && redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(key);
                if (json != null) {
                    List<CloudInstance> instances = deserialize(json);
                    if (instances != null) {
                        log.debug("分布式缓存命中: {} - {}", provider, region);
                        if (cacheConfig.isUseLocalCache()) {
                            localCache.put(key, new CacheEntry<>(instances, cacheConfig.getTtlMinutes()));
                        }
                        return Optional.of(instances);
                    }
                }
            } catch (Exception e) {
                log.warn("分布式缓存读取失败: {}", e.getMessage());
            }
        }

        return Optional.empty();
    }

    public void putToCache(CloudProvider provider, String region, List<CloudInstance> instances) {
        if (instances == null || instances.isEmpty()) {
            return;
        }

        String key = buildCacheKey(provider, region);
        List<CloudInstance> copy = deepCopy(instances);

        if (cacheConfig.isUseLocalCache()) {
            localCache.put(key, new CacheEntry<>(copy, cacheConfig.getTtlMinutes()));
            log.debug("本地缓存已更新: {} - {} ({} 个实例)", provider, region, instances.size());
        }

        if (cacheConfig.isUseDistributedCache() && redisTemplate != null) {
            try {
                String json = serialize(copy);
                redisTemplate.opsForValue().set(
                    key,
                    json,
                    cacheConfig.getTtlMinutes(),
                    TimeUnit.MINUTES
                );
                log.debug("分布式缓存已更新: {} - {} ({} 个实例)", provider, region, instances.size());
            } catch (Exception e) {
                log.warn("分布式缓存写入失败: {}", e.getMessage());
            }
        }
    }

    public void invalidateCache(CloudProvider provider, String region) {
        String key = buildCacheKey(provider, region);
        localCache.remove(key);

        if (redisTemplate != null) {
            redisTemplate.delete(key);
        }

        log.info("缓存已失效: {} - {}", provider, region);
    }

    public void invalidateAllForProvider(CloudProvider provider) {
        String prefix = CACHE_PREFIX + provider.getCode() + ":";
        
        localCache.keySet().removeIf(key -> key.startsWith(prefix));

        if (redisTemplate != null) {
            try {
                var keys = redisTemplate.keys(prefix + "*");
                if (keys != null && !keys.isEmpty()) {
                    redisTemplate.delete(keys);
                }
            } catch (Exception e) {
                log.warn("批量清理分布式缓存失败: {}", e.getMessage());
            }
        }

        log.info("已清理 {} 的所有缓存", provider);
    }

    private String buildCacheKey(CloudProvider provider, String region) {
        return CACHE_PREFIX + provider.getCode() + ":" + (region != null ? region : "all");
    }

    private String serialize(List<CloudInstance> instances) throws JsonProcessingException {
        return objectMapper.writeValueAsString(instances);
    }

    private List<CloudInstance> deserialize(String json) throws JsonProcessingException {
        return objectMapper.readValue(json, new TypeReference<List<CloudInstance>>() {});
    }

    private List<CloudInstance> deepCopy(List<CloudInstance> original) {
        if (original == null) {
            return Collections.emptyList();
        }
        try {
            return deserialize(serialize(original));
        } catch (JsonProcessingException e) {
            log.warn("深拷贝失败，返回新的 ArrayList: {}", e.getMessage());
            return new ArrayList<>(original);
        }
    }

    public static class CacheEntry<T> {
        private final T value;
        private final long expireAt;

        public CacheEntry(T value, long ttlMinutes) {
            this.value = value;
            this.expireAt = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(ttlMinutes);
        }

        public T getValue() {
            return value;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expireAt;
        }

        public boolean isExpired(long now) {
            return now > expireAt;
        }
    }
}
