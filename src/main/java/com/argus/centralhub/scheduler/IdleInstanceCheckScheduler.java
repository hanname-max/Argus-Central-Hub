package com.argus.centralhub.scheduler;

import com.argus.centralhub.config.ShutdownRuleProperties;
import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.repository.CloudInstanceRepository;
import com.argus.centralhub.shutdown.InstanceShutdownExecutor;
import com.argus.centralhub.shutdown.ShutdownRuleEngine;
import com.argus.centralhub.shutdown.rule.RuleResult;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
@ConditionalOnProperty(
        prefix = "shutdown-rule",
        name = "enabled",
        havingValue = "true"
)
public class IdleInstanceCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(IdleInstanceCheckScheduler.class);

    private final CloudInstanceRepository cloudInstanceRepository;
    private final ShutdownRuleEngine ruleEngine;
    private final InstanceShutdownExecutor shutdownExecutor;
    private final ShutdownRuleProperties properties;

    public IdleInstanceCheckScheduler(
            CloudInstanceRepository cloudInstanceRepository,
            ShutdownRuleEngine ruleEngine,
            InstanceShutdownExecutor shutdownExecutor,
            ShutdownRuleProperties properties) {
        this.cloudInstanceRepository = cloudInstanceRepository;
        this.ruleEngine = ruleEngine;
        this.shutdownExecutor = shutdownExecutor;
        this.properties = properties;
    }

    @Scheduled(cron = "${shutdown-rule.cron:0 0 * * * ?}")
    @SchedulerLock(
            name = "checkIdleInstances",
            lockAtLeastFor = "1m",
            lockAtMostFor = "30m"
    )
    public void checkIdleInstances() {
        log.info("开始执行闲置实例检查任务...");

        try {
            List<CloudInstance> runningInstances = cloudInstanceRepository.findByStatus(InstanceStatus.RUNNING);

            log.info("发现 {} 个运行中的实例", runningInstances.size());

            int shutdownCount = 0;
            int skipCount = 0;

            for (CloudInstance instance : runningInstances) {
                Optional<RuleResult> resultOpt = ruleEngine.evaluate(instance);

                if (resultOpt.isPresent()) {
                    RuleResult result = resultOpt.get();

                    boolean success = shutdownExecutor.shutdownInstance(
                            instance,
                            String.format("规则[%s]触发: %s", result.getRuleName(), result.getReason())
                    );

                    if (success) {
                        shutdownCount++;
                    }
                } else {
                    skipCount++;
                    log.debug("跳过实例 {}/{}: 无匹配的关停规则",
                            instance.getCloudProvider().getLabel(),
                            instance.getInstanceId());
                }
            }

            log.info("闲置实例检查任务完成: 关停 {} 个, 跳过 {} 个", shutdownCount, skipCount);

        } catch (Exception e) {
            log.error("闲置实例检查任务执行失败: {}", e.getMessage(), e);
        }
    }
}
