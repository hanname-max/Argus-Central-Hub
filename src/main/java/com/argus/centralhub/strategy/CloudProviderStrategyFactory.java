package com.argus.centralhub.strategy;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.exception.BusinessException;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class CloudProviderStrategyFactory {

    private final List<CloudProviderStrategy> strategies;
    private final Map<CloudProvider, CloudProviderStrategy> strategyMap = new EnumMap<>(CloudProvider.class);

    public CloudProviderStrategyFactory(List<CloudProviderStrategy> strategies) {
        this.strategies = strategies;
    }

    @PostConstruct
    public void init() {
        for (CloudProviderStrategy strategy : strategies) {
            strategyMap.put(strategy.getProviderType(), strategy);
        }
    }

    public CloudProviderStrategy getStrategy(CloudProvider provider) {
        CloudProviderStrategy strategy = strategyMap.get(provider);
        if (strategy == null) {
            throw new BusinessException("不支持的云厂商类型: " + provider);
        }
        return strategy;
    }

    public List<CloudProvider> listSupportedProviders() {
        return new ArrayList<>(strategyMap.keySet());
    }

    public boolean isSupported(CloudProvider provider) {
        return strategyMap.containsKey(provider);
    }

    public List<CloudProviderStrategy> listAllStrategies() {
        return new ArrayList<>(strategies);
    }

    public List<CloudProviderStrategy> listRealStrategies() {
        List<CloudProviderStrategy> realStrategies = new ArrayList<>();
        for (CloudProviderStrategy strategy : strategies) {
            if (strategy.getProviderType() != CloudProvider.OTHER) {
                realStrategies.add(strategy);
            }
        }
        return realStrategies;
    }
}
