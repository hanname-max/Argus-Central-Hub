package com.argus.centralhub.service;

import com.argus.centralhub.config.InstanceSyncProperties;
import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.event.InstanceStatusChangedEvent;
import com.argus.centralhub.repository.CloudInstanceRepository;
import com.argus.centralhub.strategy.CloudProviderStrategy;
import com.argus.centralhub.strategy.CloudProviderStrategyFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class InstanceSyncService {

    private static final Logger log = LoggerFactory.getLogger(InstanceSyncService.class);

    private final CloudInstanceService cloudInstanceService;
    private final CloudInstanceRepository cloudInstanceRepository;
    private final CloudProviderStrategyFactory strategyFactory;
    private final InstanceSyncProperties syncProperties;
    private final ApplicationEventPublisher eventPublisher;

    public InstanceSyncService(
            CloudInstanceService cloudInstanceService,
            CloudInstanceRepository cloudInstanceRepository,
            CloudProviderStrategyFactory strategyFactory,
            InstanceSyncProperties syncProperties,
            ApplicationEventPublisher eventPublisher) {
        this.cloudInstanceService = cloudInstanceService;
        this.cloudInstanceRepository = cloudInstanceRepository;
        this.strategyFactory = strategyFactory;
        this.syncProperties = syncProperties;
        this.eventPublisher = eventPublisher;
    }

    public void syncAllInstances() {
        log.info("开始同步全量云主机状态...");

        List<CloudInstance> fetchedInstances = new ArrayList<>();

        for (CloudProviderStrategy strategy : strategyFactory.listRealStrategies()) {
            try {
                CloudProvider provider = strategy.getProviderType();
                List<String> regions = getRegionsForProvider(provider);

                for (String region : regions) {
                    log.info("开始从 {} 同步实例 (区域: {})", provider.getLabel(), region);

                    List<CloudInstance> providerInstances = cloudInstanceService.listAllInstances(region, true);
                    fetchedInstances.addAll(providerInstances);

                    log.info("从 {} {} 区域获取到 {} 个实例", provider.getLabel(), region, providerInstances.size());
                }

            } catch (Exception e) {
                log.error("同步实例失败: {}", e.getMessage(), e);
            }
        }

        log.info("总共从云服务商获取到 {} 个实例", fetchedInstances.size());

        if (!fetchedInstances.isEmpty()) {
            saveInstancesToDatabase(fetchedInstances);
        }

        log.info("全量云主机状态同步完成");
    }

    private List<String> getRegionsForProvider(CloudProvider provider) {
        List<String> regions = syncProperties.getRegions().get(provider);
        if (regions == null || regions.isEmpty()) {
            regions = List.of(syncProperties.getDefaultRegion());
        }
        return regions;
    }

    @Transactional
    public void saveInstancesToDatabase(List<CloudInstance> fetchedInstances) {
        if (fetchedInstances.isEmpty()) {
            return;
        }

        upsertInstances(fetchedInstances);
        markTerminatedInstances(fetchedInstances);
    }

    @Transactional
    public void upsertInstances(List<CloudInstance> fetchedInstances) {
        if (fetchedInstances.isEmpty()) {
            return;
        }

        Map<CloudProvider, List<CloudInstance>> instancesByProvider = fetchedInstances.stream()
                .collect(Collectors.groupingBy(CloudInstance::getCloudProvider));

        for (Map.Entry<CloudProvider, List<CloudInstance>> entry : instancesByProvider.entrySet()) {
            CloudProvider provider = entry.getKey();
            List<CloudInstance> providerInstances = entry.getValue();

            List<String> instanceIds = providerInstances.stream()
                    .map(CloudInstance::getInstanceId)
                    .collect(Collectors.toList());

            List<CloudInstance> existingInstances = cloudInstanceRepository
                    .findByCloudProviderAndInstanceIds(provider, instanceIds);

            Map<String, CloudInstance> existingMap = existingInstances.stream()
                    .collect(Collectors.toMap(CloudInstance::getInstanceId, i -> i));

            List<CloudInstance> toSave = new ArrayList<>();

            for (CloudInstance fetched : providerInstances) {
                CloudInstance existing = existingMap.get(fetched.getInstanceId());

                if (existing != null) {
                    updateInstance(existing, fetched);
                    toSave.add(existing);
                } else {
                    toSave.add(fetched);
                }
            }

            if (!toSave.isEmpty()) {
                cloudInstanceRepository.saveAll(toSave);
                log.info("{} 已更新/插入 {} 个实例", provider.getLabel(), toSave.size());
            }
        }
    }

    private void updateInstance(CloudInstance existing, CloudInstance fetched) {
        if (fetched.getInstanceName() != null && !fetched.getInstanceName().equals(existing.getInstanceName())) {
            existing.rename(fetched.getInstanceName());
        }

        if (fetched.getStatus() != existing.getStatus()) {
            updateInstanceStatus(existing, fetched.getStatus());
        }

        if (fetched.getPrivateIp() != null || fetched.getPublicIp() != null) {
            String newPrivateIp = fetched.getPrivateIp() != null ? fetched.getPrivateIp() : existing.getPrivateIp();
            String newPublicIp = fetched.getPublicIp() != null ? fetched.getPublicIp() : existing.getPublicIp();
            existing.assignIp(newPrivateIp, newPublicIp);
        }
    }

    private void updateInstanceStatus(CloudInstance instance, InstanceStatus newStatus) {
        InstanceStatus oldStatus = instance.getStatus();
        try {
            switch (newStatus) {
                case RUNNING:
                    instance.start();
                    break;
                case STOPPED:
                    instance.stop();
                    break;
                case TERMINATED:
                    instance.terminate();
                    break;
                default:
                    break;
            }
            publishStatusChangedEvent(instance, oldStatus, newStatus);
        } catch (Exception e) {
            log.debug("实例状态更新被领域规则阻止: {}/{} - {}",
                    instance.getCloudProvider().getLabel(), instance.getInstanceId(), e.getMessage());
        }
    }

    private void publishStatusChangedEvent(CloudInstance instance, InstanceStatus oldStatus, InstanceStatus newStatus) {
        InstanceStatusChangedEvent event = new InstanceStatusChangedEvent(
                instance.getInstanceId(),
                instance.getCloudProvider(),
                instance.getInstanceName(),
                instance.getRegion(),
                oldStatus,
                newStatus
        );
        eventPublisher.publishEvent(event);
        log.debug("已发布实例状态变更事件: {}/{} - {} -> {}",
                instance.getCloudProvider().getLabel(),
                instance.getInstanceId(),
                oldStatus,
                newStatus);
    }

    @Transactional
    public void markTerminatedInstances(List<CloudInstance> fetchedInstances) {
        if (fetchedInstances.isEmpty()) {
            return;
        }

        Map<CloudProvider, Set<String>> fetchedIdsByProvider = new HashMap<>();

        for (CloudInstance instance : fetchedInstances) {
            fetchedIdsByProvider
                    .computeIfAbsent(instance.getCloudProvider(), k -> new HashSet<>())
                    .add(instance.getInstanceId());
        }

        for (CloudProviderStrategy strategy : strategyFactory.listRealStrategies()) {
            CloudProvider provider = strategy.getProviderType();
            Set<String> fetchedIds = fetchedIdsByProvider.getOrDefault(provider, Set.of());

            List<String> dbIds = cloudInstanceRepository.findAllInstanceIdsByProvider(provider);

            List<String> terminatedIds = dbIds.stream()
                    .filter(id -> !fetchedIds.contains(id))
                    .collect(Collectors.toList());

            if (!terminatedIds.isEmpty()) {
                List<CloudInstance> terminatedInstances = cloudInstanceRepository
                        .findByCloudProviderAndInstanceIds(provider, terminatedIds);

                int updatedCount = cloudInstanceRepository.markAsTerminatedByProviderAndInstanceIds(
                        provider, terminatedIds, InstanceStatus.TERMINATED);
                log.info("{} 已标记 {} 个实例为已销毁", provider.getLabel(), updatedCount);

                for (CloudInstance instance : terminatedInstances) {
                    InstanceStatus oldStatus = instance.getStatus();
                    if (oldStatus != InstanceStatus.TERMINATED) {
                        publishStatusChangedEvent(instance, oldStatus, InstanceStatus.TERMINATED);
                    }
                }
            }
        }
    }
}
