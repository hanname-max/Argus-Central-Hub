package com.argus.centralhub.service;

import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.strategy.CloudProviderStrategy;
import com.argus.centralhub.strategy.CloudProviderStrategyFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

@Service
public class CloudInstanceService {

    private static final Logger log = LoggerFactory.getLogger(CloudInstanceService.class);
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(10);

    private final CloudProviderStrategyFactory strategyFactory;
    private final ExecutorService virtualThreadExecutor;

    public CloudInstanceService(CloudProviderStrategyFactory strategyFactory) {
        this.strategyFactory = strategyFactory;
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
        return listAllInstances(region, DEFAULT_TIMEOUT);
    }

    public List<CloudInstance> listAllInstances(String region, Duration timeout) {
        List<CloudProviderStrategy> strategies = strategyFactory.listRealStrategies();
        List<CloudInstance> allInstances = new ArrayList<>();
        List<Future<List<CloudInstance>>> futures = new ArrayList<>();
        long startTime = System.currentTimeMillis();

        for (CloudProviderStrategy strategy : strategies) {
            Future<List<CloudInstance>> future = virtualThreadExecutor.submit(() -> {
                try {
                    log.info("开始调用 {} 的实例列表 API", strategy.getProviderType().getLabel());
                    List<CloudInstance> instances = strategy.listInstances(region);
                    log.info("{} 调用成功，获取到 {} 个实例", 
                            strategy.getProviderType().getLabel(), instances.size());
                    return instances;
                } catch (Exception e) {
                    log.error("{} 调用失败: {}", strategy.getProviderType().getLabel(), e.getMessage());
                    throw e;
                }
            });
            futures.add(future);
        }

        boolean isGlobalTimeout = false;
        for (int i = 0; i < futures.size(); i++) {
            Future<List<CloudInstance>> future = futures.get(i);
            CloudProviderStrategy strategy = strategies.get(i);

            if (isGlobalTimeout) {
                log.warn("全局超时，跳过等待 {} 的结果", strategy.getProviderType().getLabel());
                future.cancel(false);
                continue;
            }

            long elapsedMillis = System.currentTimeMillis() - startTime;
            long remainingMillis = timeout.toMillis() - elapsedMillis;

            if (remainingMillis <= 0) {
                log.warn("全局超时已到，{} 调用已超时，已降级处理", strategy.getProviderType().getLabel());
                future.cancel(false);
                isGlobalTimeout = true;
                continue;
            }

            try {
                List<CloudInstance> instances = future.get(remainingMillis, TimeUnit.MILLISECONDS);
                allInstances.addAll(instances);
            } catch (TimeoutException e) {
                log.warn("{} 调用超时，已降级处理", strategy.getProviderType().getLabel());
                future.cancel(false);
                isGlobalTimeout = true;
            } catch (InterruptedException e) {
                log.warn("{} 调用被中断", strategy.getProviderType().getLabel());
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                log.error("{} 调用执行失败: {}", strategy.getProviderType().getLabel(), e.getMessage());
            }
        }

        log.info("总共获取到 {} 个云实例", allInstances.size());
        return allInstances;
    }
}