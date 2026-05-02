package com.argus.centralhub.event;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;

import java.time.LocalDateTime;

public record InstanceStatusChangedEvent(
        String instanceId,
        CloudProvider cloudProvider,
        String instanceName,
        String region,
        InstanceStatus oldStatus,
        InstanceStatus newStatus,
        LocalDateTime occurredAt
) {
    public InstanceStatusChangedEvent(
            String instanceId,
            CloudProvider cloudProvider,
            String instanceName,
            String region,
            InstanceStatus oldStatus,
            InstanceStatus newStatus
    ) {
        this(instanceId, cloudProvider, instanceName, region, oldStatus, newStatus, LocalDateTime.now());
    }
}
