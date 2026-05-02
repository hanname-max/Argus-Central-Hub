package com.argus.centralhub.mapper;

import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static java.util.Map.entry;

@Component
public class InstanceStatusMapper {

    private static final Logger log = LoggerFactory.getLogger(InstanceStatusMapper.class);

    private final Map<CloudProvider, StatusMappingRule> providerRules = new HashMap<>();

    public InstanceStatusMapper() {
        initializeRules();
    }

    private void initializeRules() {
        providerRules.put(CloudProvider.AWS, AwsStatusRule.INSTANCE);
        providerRules.put(CloudProvider.AZURE, AzureStatusRule.INSTANCE);
        providerRules.put(CloudProvider.GOOGLE_CLOUD, GoogleCloudStatusRule.INSTANCE);
        providerRules.put(CloudProvider.ALIYUN, AliyunStatusRule.INSTANCE);
        providerRules.put(CloudProvider.TENCENT, TencentStatusRule.INSTANCE);
        providerRules.put(CloudProvider.HUAWEI, HuaweiStatusRule.INSTANCE);
        providerRules.put(CloudProvider.VOLCENGINE, VolcengineStatusRule.INSTANCE);
        providerRules.put(CloudProvider.JD_CLOUD, JcloudStatusRule.INSTANCE);
        providerRules.put(CloudProvider.ORACLE, OracleStatusRule.INSTANCE);
    }

    public InstanceStatus mapStatus(CloudProvider provider, String providerStatus) {
        if (providerStatus == null || providerStatus.isBlank()) {
            log.warn("云服务商 {} 提供了空状态，使用默认值 UNKNOWN", provider);
            return InstanceStatus.ERROR;
        }

        String normalizedStatus = providerStatus.trim().toLowerCase();
        StatusMappingRule rule = providerRules.getOrDefault(provider, DefaultStatusRule.INSTANCE);

        InstanceStatus result = rule.map(normalizedStatus);

        if (result == null) {
            log.warn("云服务商 {} 的未知状态: '{}'，使用兜底策略", provider, providerStatus);
            result = fallbackMapping(normalizedStatus);
        }

        log.debug("状态映射: [{}] {} -> {}", provider, providerStatus, result);
        return result;
    }

    private InstanceStatus fallbackMapping(String normalizedStatus) {
        if (containsAny(normalizedStatus, "run", "active", "available")) {
            return InstanceStatus.RUNNING;
        }
        if (containsAny(normalizedStatus, "stop", "halt", "offline")) {
            return InstanceStatus.STOPPED;
        }
        if (containsAny(normalizedStatus, "pending", "wait", "queue")) {
            return InstanceStatus.PENDING;
        }
        if (containsAny(normalizedStatus, "start", "boot")) {
            return InstanceStatus.STARTING;
        }
        if (containsAny(normalizedStatus, "terminat", "destroy", "delete")) {
            return InstanceStatus.TERMINATED;
        }
        if (containsAny(normalizedStatus, "error", "fail", "fault", "crash")) {
            return InstanceStatus.ERROR;
        }
        return InstanceStatus.ERROR;
    }

    private boolean containsAny(String text, String... keywords) {
        return Arrays.stream(keywords).anyMatch(text::contains);
    }

    interface StatusMappingRule {
        InstanceStatus map(String normalizedStatus);
    }

    enum AwsStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("pending", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("shutting-down", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("stopping", InstanceStatus.STOPPING),
            entry("terminated", InstanceStatus.TERMINATED)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum AzureStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("vm starting", InstanceStatus.STARTING),
            entry("vm running", InstanceStatus.RUNNING),
            entry("vm stopping", InstanceStatus.STOPPING),
            entry("vm stopped", InstanceStatus.STOPPED),
            entry("vm deallocating", InstanceStatus.STOPPING),
            entry("vm deallocated", InstanceStatus.STOPPED),
            entry("starting", InstanceStatus.STARTING),
            entry("running", InstanceStatus.RUNNING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("deallocating", InstanceStatus.STOPPING),
            entry("deallocated", InstanceStatus.STOPPED)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum GoogleCloudStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("provisioning", InstanceStatus.PENDING),
            entry("staging", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("suspending", InstanceStatus.STOPPING),
            entry("suspended", InstanceStatus.STOPPED),
            entry("terminated", InstanceStatus.TERMINATED)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum AliyunStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("pending", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("starting", InstanceStatus.STARTING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("shutting-down", InstanceStatus.TERMINATING),
            entry("terminated", InstanceStatus.TERMINATED)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum TencentStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("pending", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("starting", InstanceStatus.STARTING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("shutting-down", InstanceStatus.TERMINATING),
            entry("terminated", InstanceStatus.TERMINATED)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum HuaweiStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("build", InstanceStatus.PENDING),
            entry("active", InstanceStatus.RUNNING),
            entry("reboot", InstanceStatus.RESTARTING),
            entry("hard_reboot", InstanceStatus.RESTARTING),
            entry("shutoff", InstanceStatus.STOPPED),
            entry("resize", InstanceStatus.PENDING),
            entry("verify_resize", InstanceStatus.PENDING),
            entry("revert_resize", InstanceStatus.PENDING),
            entry("shelving", InstanceStatus.STOPPING),
            entry("shelved", InstanceStatus.STOPPED),
            entry("shelved_offloaded", InstanceStatus.STOPPED),
            entry("unshelving", InstanceStatus.STARTING),
            entry("image", InstanceStatus.PENDING),
            entry("snapshot", InstanceStatus.PENDING),
            entry("deleting", InstanceStatus.TERMINATING),
            entry("deleted", InstanceStatus.TERMINATED),
            entry("error", InstanceStatus.ERROR),
            entry("stopped", InstanceStatus.STOPPED),
            entry("stopping", InstanceStatus.STOPPING),
            entry("running", InstanceStatus.RUNNING),
            entry("pending", InstanceStatus.PENDING)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum VolcengineStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("pending", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("starting", InstanceStatus.STARTING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("rebooting", InstanceStatus.RESTARTING),
            entry("terminating", InstanceStatus.TERMINATING),
            entry("terminated", InstanceStatus.TERMINATED),
            entry("error", InstanceStatus.ERROR)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum JcloudStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("pending", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("starting", InstanceStatus.STARTING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("rebooting", InstanceStatus.RESTARTING),
            entry("terminating", InstanceStatus.TERMINATING),
            entry("terminated", InstanceStatus.TERMINATED),
            entry("error", InstanceStatus.ERROR)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }

    enum DefaultStatusRule implements StatusMappingRule {
        INSTANCE;

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return null;
        }
    }

    enum OracleStatusRule implements StatusMappingRule {
        INSTANCE;

        private static final Map<String, InstanceStatus> EXACT_MAPPINGS = Map.ofEntries(
            entry("provisioning", InstanceStatus.PENDING),
            entry("running", InstanceStatus.RUNNING),
            entry("starting", InstanceStatus.STARTING),
            entry("stopping", InstanceStatus.STOPPING),
            entry("stopped", InstanceStatus.STOPPED),
            entry("terminating", InstanceStatus.TERMINATING),
            entry("terminated", InstanceStatus.TERMINATED),
            entry("crashed", InstanceStatus.ERROR),
            entry("failed", InstanceStatus.ERROR),
            entry("creating_image", InstanceStatus.PENDING),
            entry("image_accepted", InstanceStatus.RUNNING),
            entry("detaching", InstanceStatus.PENDING),
            entry("attaching", InstanceStatus.PENDING),
            entry("terminating_pv", InstanceStatus.TERMINATING)
        );

        @Override
        public InstanceStatus map(String normalizedStatus) {
            return EXACT_MAPPINGS.get(normalizedStatus);
        }
    }
}
