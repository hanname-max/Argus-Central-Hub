package com.argus.centralhub.config;

import com.argus.centralhub.domain.enums.CloudProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@ConfigurationProperties(prefix = "scheduler.instance-sync")
public class InstanceSyncProperties {

    private boolean enabled = true;
    private String cron = "0 */5 * * * ?";
    private String defaultRegion = "cn-east-1";
    private Map<CloudProvider, List<String>> regions = new HashMap<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getCron() {
        return cron;
    }

    public void setCron(String cron) {
        this.cron = cron;
    }

    public String getDefaultRegion() {
        return defaultRegion;
    }

    public void setDefaultRegion(String defaultRegion) {
        this.defaultRegion = defaultRegion;
    }

    public Map<CloudProvider, List<String>> getRegions() {
        return regions;
    }

    public void setRegions(Map<CloudProvider, List<String>> regions) {
        this.regions = regions;
    }
}
