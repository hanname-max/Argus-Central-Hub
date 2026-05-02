package com.argus.centralhub.strategy.impl;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.strategy.AbstractCloudProviderStrategy;
import org.springframework.stereotype.Component;

@Component
public class HuaweiStrategy extends AbstractCloudProviderStrategy {

    @Override
    public CloudProvider getProviderType() {
        return CloudProvider.HUAWEI;
    }
}
