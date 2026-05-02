package com.argus.centralhub.scheduler;

import com.argus.centralhub.config.InstanceSyncProperties;
import com.argus.centralhub.service.InstanceSyncService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        prefix = "scheduler.instance-sync",
        name = "enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class InstanceSyncScheduler {

    private static final Logger log = LoggerFactory.getLogger(InstanceSyncScheduler.class);

    private final InstanceSyncService instanceSyncService;
    private final InstanceSyncProperties syncProperties;

    public InstanceSyncScheduler(
            InstanceSyncService instanceSyncService,
            InstanceSyncProperties syncProperties) {
        this.instanceSyncService = instanceSyncService;
        this.syncProperties = syncProperties;
    }

    @Scheduled(cron = "${scheduler.instance-sync.cron:0 */5 * * * ?}")
    @SchedulerLock(
            name = "syncAllInstances",
            lockAtLeastFor = "4m",
            lockAtMostFor = "10m"
    )
    public void syncAllInstances() {
        log.info("开始执行定时任务：同步全量云主机状态");
        try {
            instanceSyncService.syncAllInstances();
            log.info("定时任务执行完成：同步全量云主机状态");
        } catch (Exception e) {
            log.error("定时任务执行失败：{}", e.getMessage(), e);
        }
    }
}
