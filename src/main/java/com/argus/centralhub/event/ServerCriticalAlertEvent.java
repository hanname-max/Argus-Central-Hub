package com.argus.centralhub.event;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;

import java.time.LocalDateTime;

public record ServerCriticalAlertEvent(
        String instanceId,
        CloudProvider cloudProvider,
        String instanceName,
        String region,
        InstanceStatus instanceStatus,
        AlertType alertType,
        String errorMessage,
        String stackTrace,
        LocalDateTime occurredAt,
        String extraInfo
) {
    public ServerCriticalAlertEvent(
            String instanceId,
            CloudProvider cloudProvider,
            String instanceName,
            String region,
            InstanceStatus instanceStatus,
            AlertType alertType,
            String errorMessage,
            String stackTrace
    ) {
        this(instanceId, cloudProvider, instanceName, region, instanceStatus, 
             alertType, errorMessage, stackTrace, LocalDateTime.now(), null);
    }

    public String getShortErrorMessage() {
        if (errorMessage != null && errorMessage.length() > 100) {
            return errorMessage.substring(0, 100) + "...";
        }
        return errorMessage != null ? errorMessage : "无";
    }

    public String getShortStackTrace() {
        if (stackTrace != null && stackTrace.length() > 500) {
            return stackTrace.substring(0, 500) + "...";
        }
        return stackTrace != null ? stackTrace : "无";
    }
}
