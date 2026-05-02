package com.argus.centralhub.listener;

import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.event.InstanceStatusChangedEvent;
import com.argus.centralhub.repository.CloudInstanceRepository;
import com.argus.centralhub.shutdown.InstanceShutdownExecutor;
import com.argus.centralhub.shutdown.ShutdownRuleEngine;
import com.argus.centralhub.shutdown.rule.RuleResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "shutdown-rule",
        name = "enabled",
        havingValue = "true"
)
public class ShutdownRuleEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShutdownRuleEventListener.class);

    private final ShutdownRuleEngine ruleEngine;
    private final InstanceShutdownExecutor shutdownExecutor;
    private final CloudInstanceRepository cloudInstanceRepository;

    public ShutdownRuleEventListener(
            ShutdownRuleEngine ruleEngine,
            InstanceShutdownExecutor shutdownExecutor,
            CloudInstanceRepository cloudInstanceRepository) {
        this.ruleEngine = ruleEngine;
        this.shutdownExecutor = shutdownExecutor;
        this.cloudInstanceRepository = cloudInstanceRepository;
    }

    @Async
    @EventListener
    public void handleInstanceStatusChanged(InstanceStatusChangedEvent event) {
        if (event.newStatus() != InstanceStatus.RUNNING) {
            log.debug("实例状态非 RUNNING，跳过关停规则检查: {}/{}, 状态: {}",
                    event.cloudProvider().getLabel(),
                    event.instanceId(),
                    event.newStatus());
            return;
        }

        log.info("实例变为运行状态，开始检查关停规则: {}/{}",
                event.cloudProvider().getLabel(),
                event.instanceId());

        Optional<CloudInstance> instanceOpt = cloudInstanceRepository
                .findByInstanceIdAndCloudProvider(event.instanceId(), event.cloudProvider());

        if (instanceOpt.isEmpty()) {
            log.warn("无法在数据库中找到实例: {}/{}",
                    event.cloudProvider().getLabel(),
                    event.instanceId());
            return;
        }

        CloudInstance instance = instanceOpt.get();

        Optional<RuleResult> resultOpt = ruleEngine.evaluate(instance);

        if (resultOpt.isPresent()) {
            RuleResult result = resultOpt.get();
            log.info("关停规则触发，准备关停实例: {}/{}, 规则: {}, 原因: {}",
                    instance.getCloudProvider().getLabel(),
                    instance.getInstanceId(),
                    result.getRuleName(),
                    result.getReason());

            boolean success = shutdownExecutor.shutdownInstance(
                    instance,
                    String.format("事件驱动-规则[%s]触发: %s", result.getRuleName(), result.getReason())
            );

            if (success) {
                log.info("事件驱动关停实例成功: {}/{}",
                        instance.getCloudProvider().getLabel(),
                        instance.getInstanceId());
            } else {
                log.warn("事件驱动关停实例失败: {}/{}",
                        instance.getCloudProvider().getLabel(),
                        instance.getInstanceId());
            }
        } else {
            log.debug("实例 {}/{} 无匹配的关停规则，不执行关停",
                    instance.getCloudProvider().getLabel(),
                    instance.getInstanceId());
        }
    }
}
