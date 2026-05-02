package com.argus.centralhub.strategy;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.model.CloudInstance;

import java.util.List;

public interface CloudProviderStrategy {

    CloudProvider getProviderType();

    default String getProviderCode() {
        return getProviderType().getCode();
    }

    List<CloudInstance> listInstances(String region);

    CloudInstance getInstanceById(String instanceId, String region);

    boolean startInstance(String instanceId, String region);

    boolean stopInstance(String instanceId, String region);

    boolean restartInstance(String instanceId, String region);

    boolean terminateInstance(String instanceId, String region);
}
