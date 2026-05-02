package com.argus.centralhub.listener;

import com.argus.centralhub.event.InstanceStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class InstanceStatusChangeListener {

    private static final Logger log = LoggerFactory.getLogger(InstanceStatusChangeListener.class);

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleInstanceStatusChanged(InstanceStatusChangedEvent event) {
        log.info("实例状态变更事件: 实例ID={}, 云服务商={}, 实例名称={}, 区域={}, 旧状态={}, 新状态={}, 发生时间={}",
                event.instanceId(),
                event.cloudProvider().getLabel(),
                event.instanceName() != null ? event.instanceName() : "N/A",
                event.region(),
                event.oldStatus(),
                event.newStatus(),
                event.occurredAt());
    }
}
