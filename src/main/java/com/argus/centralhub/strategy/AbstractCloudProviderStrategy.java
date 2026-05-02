package com.argus.centralhub.strategy;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.model.CloudInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;

public abstract class AbstractCloudProviderStrategy implements CloudProviderStrategy {

    private static final Logger log = LoggerFactory.getLogger(AbstractCloudProviderStrategy.class);

    @Override
    public abstract CloudProvider getProviderType();

    protected String getProviderLabel() {
        return getProviderType().getLabel();
    }

    @Override
    public List<CloudInstance> listInstances(String region) {
        log.info("调用{} API，获取{}区域的云实例列表", getProviderLabel(), region);
        return Collections.emptyList();
    }

    @Override
    public CloudInstance getInstanceById(String instanceId, String region) {
        log.info("调用{} API，获取{}区域实例ID为{}的云实例详情", getProviderLabel(), region, instanceId);
        return null;
    }

    @Override
    public boolean startInstance(String instanceId, String region) {
        log.info("调用{} API，启动{}区域实例ID为{}的云实例", getProviderLabel(), region, instanceId);
        return true;
    }

    @Override
    public boolean stopInstance(String instanceId, String region) {
        log.info("调用{} API，停止{}区域实例ID为{}的云实例", getProviderLabel(), region, instanceId);
        return true;
    }

    @Override
    public boolean restartInstance(String instanceId, String region) {
        log.info("调用{} API，重启{}区域实例ID为{}的云实例", getProviderLabel(), region, instanceId);
        return true;
    }

    @Override
    public boolean terminateInstance(String instanceId, String region) {
        log.info("调用{} API，销毁{}区域实例ID为{}的云实例", getProviderLabel(), region, instanceId);
        return true;
    }
}
