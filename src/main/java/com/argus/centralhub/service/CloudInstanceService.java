package com.argus.centralhub.service;

import com.argus.centralhub.cache.InstanceCacheService;
import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.ratelimit.RateLimiterService;
import com.argus.centralhub.strategy.CloudProviderStrategy;
import com.argus.centralhub.strategy.CloudProviderStrategyFactory;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;

@Service
public class CloudInstanceService {

    private static final Logger log = LoggerFactory.getLogger(CloudInstanceService.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);
    private static final String OPERATION_LIST_INSTANCES = "listInstances";

    private final CloudProviderStrategyFactory strategyFactory;
    private final RateLimiterService rateLimiterService;
    private final InstanceCacheService instanceCacheService;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final ExecutorService virtualThreadExecutor;

    public CloudInstanceService(
            CloudProviderStrategyFactory strategyFactory,
            RateLimiterService rateLimiterService,
            InstanceCacheService instanceCacheService,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.strategyFactory = strategyFactory;
        this.rateLimiterService = rateLimiterService;
        this.instanceCacheService = instanceCacheService;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    public void shutdown() {
        log.info("正在优雅关闭 CloudInstanceService 线程池...");
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("线程池未在 5 秒内完全终止，部分任务可能被中断");
            }
        } catch (InterruptedException e) {
            log.warn("等待线程池终止时被中断");
            Thread.currentThread().interrupt();
        }
        log.info("CloudInstanceService 线程池已关闭");
    }

    public List<CloudInstance> listAllInstances(String region) {
        return listAllInstances(region, false);
    }

    public List<CloudInstance> listAllInstances(String region, boolean forceRefresh) {
        if (forceRefresh) {
            log.info("强制刷新缓存: region={}", region);
            for (CloudProviderStrategy strategy : strategyFactory.listRealStrategies()) {
                instanceCacheService.invalidateCache(strategy.getProviderType(), region);
            }
        }
        return fetchInstancesInternal(region, DEFAULT_TIMEOUT);
    }

    public List<CloudInstance> listAllInstances(String region, Duration timeout) {
        return fetchInstancesInternal(region, timeout);
    }

    protected List<CloudInstance> fetchInstancesInternal(String region, Duration timeout) {
        List<CloudProviderStrategy> strategies = strategyFactory.listRealStrategies();
        List<CloudInstance> allInstances = new ArrayList<>();
        List<Future<List<CloudInstance>>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (CloudProviderStrategy strategy : strategies) {
            var provider = strategy.getProviderType();
            var operation = OPERATION_LIST_INSTANCES;

            Optional<List<CloudInstance>> cachedInstances =
                instanceCacheService.getFromCache(provider, region);

            if (cachedInstances.isPresent()) {
                log.info("使用缓存结果: {} - {} 区域", provider.getLabel(), region);
                allInstances.addAll(cachedInstances.get());
                continue;
            }

            Future<List<CloudInstance>> future = virtualThreadExecutor.submit(() -> {
                if (!rateLimiterService.tryAcquire(provider, operation)) {
                    log.warn("API 限流，跳过调用: {}", provider.getLabel());
                    return Collections.emptyList();
                }

                log.info("开始调用 {} 的实例列表 API", provider.getLabel());
                List<CloudInstance> instances = strategy.listInstances(region);
                log.info("{} 调用成功，获取到 {} 个实例",
                        provider.getLabel(), instances.size());

                instanceCacheService.putToCache(provider, region, instances);

                return instances;
            });
            futures.add(future);
        }

        boolean isGlobalTimeout = false;
        for (int i = 0; i < futures.size(); i++) {
            Future<List<CloudInstance>> future = futures.get(i);

            if (isGlobalTimeout) {
                log.warn("全局超时，跳过等待结果");
                future.cancel(false);
                continue;
            }

            long elapsedMillis = System.currentTimeMillis() - startTime;
            long remainingMillis = timeout.toMillis() - elapsedMillis;

            if (remainingMillis <= 0) {
                log.warn("全局超时已到，已降级处理");
                future.cancel(false);
                isGlobalTimeout = true;
                continue;
            }

            try {
                List<CloudInstance> instances = future.get(remainingMillis, TimeUnit.MILLISECONDS);
                allInstances.addAll(instances);
            } catch (TimeoutException e) {
                log.warn("调用超时，已降级处理");
                future.cancel(false);
                isGlobalTimeout = true;
            } catch (InterruptedException e) {
                log.warn("调用被中断");
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.error("调用执行失败: {}", e.getMessage());
            }
        }

        log.info("总共获取到 {} 个云实例", allInstances.size());
        return allInstances;
    }

    public void invalidateCacheForProvider(String providerCode) {
        try {
            CloudProvider provider = CloudProvider.fromCode(providerCode);
            instanceCacheService.invalidateAllForProvider(provider);
        } catch (IllegalArgumentException e) {
            log.warn("无效的云服务商代码: {}", providerCode);
        }
    }

    public void resetCircuitBreaker(String name) {
        circuitBreakerRegistry.circuitBreaker(name).reset();
        log.info("断路器 [{}] 已重置", name);
    }
}
