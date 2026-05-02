package com.argus.centralhub.shutdown;

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

import java.util.List;

@Service
public class InstanceShutdownExecutor {

    private static final Logger log = LoggerFactory.getLogger(InstanceShutdownExecutor.class);

    private final CloudProviderStrategyFactory strategyFactory;
    private final CloudInstanceRepository cloudInstanceRepository;
    private final ApplicationEventPublisher eventPublisher;

    public InstanceShutdownExecutor(
            CloudProviderStrategyFactory strategyFactory,
            CloudInstanceRepository cloudInstanceRepository,
            ApplicationEventPublisher eventPublisher) {
        this.strategyFactory = strategyFactory;
        this.cloudInstanceRepository = cloudInstanceRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public boolean shutdownInstance(CloudInstance instance, String reason) {
        log.info("准备关停实例: {}/{}, 原因: {}",
                instance.getCloudProvider().getLabel(),
                instance.getInstanceId(),
                reason);

        try {
            CloudProviderStrategy strategy = strategyFactory.getStrategy(instance.getCloudProvider());

            boolean apiSuccess = strategy.stopInstance(instance.getInstanceId(), instance.getRegion());

            if (apiSuccess) {
                updateInstanceStatus(instance, reason);
                log.info("实例关停成功: {}/{}",
                        instance.getCloudProvider().getLabel(),
                        instance.getInstanceId());
                return true;
            } else {
                log.warn("实例关停 API 调用未成功: {}/{}",
                        instance.getCloudProvider().getLabel(),
                        instance.getInstanceId());
                return false;
            }

        } catch (Exception e) {
            log.error("关停实例失败: {}/{}, 错误: {}",
                    instance.getCloudProvider().getLabel(),
                    instance.getInstanceId(),
                    e.getMessage(), e);
            return false;
        }
    }

    private void updateInstanceStatus(CloudInstance instance, String reason) {
        InstanceStatus oldStatus = instance.getStatus();

        try {
            instance.stop();
            cloudInstanceRepository.save(instance);

            publishStatusChangedEvent(instance, oldStatus, InstanceStatus.STOPPED, reason);

        } catch (Exception e) {
            log.warn("更新实例状态失败: {}/{}, 错误: {}",
                    instance.getCloudProvider().getLabel(),
                    instance.getInstanceId(),
                    e.getMessage());
        }
    }

    private void publishStatusChangedEvent(CloudInstance instance,
                                           InstanceStatus oldStatus,
                                           InstanceStatus newStatus,
                                           String reason) {
        InstanceStatusChangedEvent event = new InstanceStatusChangedEvent(
                instance.getInstanceId(),
                instance.getCloudProvider(),
                instance.getInstanceName(),
                instance.getRegion(),
                oldStatus,
                newStatus
        );
        eventPublisher.publishEvent(event);
        log.debug("已发布实例状态变更事件 (自动关停): {}/{} - {} -> {}, 原因: {}",
                instance.getCloudProvider().getLabel(),
                instance.getInstanceId(),
                oldStatus,
                newStatus,
                reason);
    }

    public int shutdownInstances(List<CloudInstance> instances, String reason) {
        int successCount = 0;
        for (CloudInstance instance : instances) {
            if (shutdownInstance(instance, reason)) {
                successCount++;
            }
        }
        log.info("批量关停实例完成: 成功 {}/{} 个", successCount, instances.size());
        return successCount;
    }
}
