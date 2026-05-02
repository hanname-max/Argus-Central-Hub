package com.argus.centralhub.strategy.impl;

import com.argus.centralhub.config.CloudProviderConfig;
import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.strategy.AbstractCloudProviderStrategy;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class OtherCloudStrategy extends AbstractCloudProviderStrategy {

    private static final Logger log = LoggerFactory.getLogger(OtherCloudStrategy.class);

    private final CloudProviderConfig cloudProviderConfig;
    private final Map<String, CloudProviderConfig.ThirdPartyConfig> providerConfigs = new ConcurrentHashMap<>();

    public OtherCloudStrategy(CloudProviderConfig cloudProviderConfig) {
        this.cloudProviderConfig = cloudProviderConfig;
    }

    @PostConstruct
    public void init() {
        for (CloudProviderConfig.ThirdPartyConfig config : cloudProviderConfig.getThirdParty()) {
            providerConfigs.put(config.getCode(), config);
            log.info("已注册第三方云厂商: code={}, label={}", config.getCode(), config.getLabel());
        }
    }

    @Override
    public CloudProvider getProviderType() {
        return CloudProvider.OTHER;
    }

    public void registerProvider(CloudProviderConfig.ThirdPartyConfig config) {
        providerConfigs.put(config.getCode(), config);
        log.info("动态注册第三方云厂商: code={}, label={}", config.getCode(), config.getLabel());
    }

    public void unregisterProvider(String providerCode) {
        providerConfigs.remove(providerCode);
        log.info("注销第三方云厂商: code={}", providerCode);
    }

    public List<String> listRegisteredProviders() {
        return List.copyOf(providerConfigs.keySet());
    }
}
