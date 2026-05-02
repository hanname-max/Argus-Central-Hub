package com.argus.centralhub.feishu.card;

import com.argus.centralhub.config.FeishuBotProperties;
import com.argus.centralhub.domain.enums.CloudProvider;
import com.argus.centralhub.domain.enums.InstanceStatus;
import com.argus.centralhub.domain.model.CloudInstance;
import com.argus.centralhub.event.AlertType;
import com.argus.centralhub.event.InstanceStatusChangedEvent;
import com.argus.centralhub.event.ServerCriticalAlertEvent;
import com.argus.centralhub.feishu.FeishuApiService;
import com.argus.centralhub.repository.CloudInstanceRepository;
import com.argus.centralhub.shutdown.InstanceShutdownExecutor;
import com.argus.centralhub.strategy.CloudProviderStrategy;
import com.argus.centralhub.strategy.CloudProviderStrategyFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class CardActionHandler {

    private static final Logger log = LoggerFactory.getLogger(CardActionHandler.class);

    private final CloudInstanceRepository cloudInstanceRepository;
    private final InstanceShutdownExecutor instanceShutdownExecutor;
    private final FeishuApiService feishuApiService;
    private final InstanceCardBuilder instanceCardBuilder;
    private final AlertCardBuilder alertCardBuilder;
    private final ObjectMapper objectMapper;
    private final FeishuBotProperties feishuBotProperties;
    private final CloudProviderStrategyFactory strategyFactory;
    private final ApplicationEventPublisher eventPublisher;

    public CardActionHandler(CloudInstanceRepository cloudInstanceRepository,
                              InstanceShutdownExecutor instanceShutdownExecutor,
                              FeishuApiService feishuApiService,
                              InstanceCardBuilder instanceCardBuilder,
                              AlertCardBuilder alertCardBuilder,
                              ObjectMapper objectMapper,
                              FeishuBotProperties feishuBotProperties,
                              CloudProviderStrategyFactory strategyFactory,
                              ApplicationEventPublisher eventPublisher) {
        this.cloudInstanceRepository = cloudInstanceRepository;
        this.instanceShutdownExecutor = instanceShutdownExecutor;
        this.feishuApiService = feishuApiService;
        this.instanceCardBuilder = instanceCardBuilder;
        this.alertCardBuilder = alertCardBuilder;
        this.objectMapper = objectMapper;
        this.feishuBotProperties = feishuBotProperties;
        this.strategyFactory = strategyFactory;
        this.eventPublisher = eventPublisher;
    }

    @Async
    public void handleAction(JsonNode action, JsonNode requestJson) {
        try {
            String actionType = action.has("action") ? action.get("action").asText() : null;
            JsonNode value = action.has("value") ? action.get("value") : null;
            
            Map<String, String> operatorInfo = getOperatorInfo(requestJson);
            
            log.info("处理卡片交互动作: actionType={}, operator={}", actionType, operatorInfo);

            if (value == null) {
                log.warn("卡片动作没有 value 字段");
                return;
            }

            String actionValue = value.has("action") ? value.get("action").asText() : null;
            
            if ("shutdown".equals(actionValue)) {
                handleShutdownAction(value, operatorInfo, requestJson);
            } else if ("alert_restart".equals(actionValue)) {
                handleAlertRestartAction(value, operatorInfo, requestJson);
            } else if ("alert_ignore".equals(actionValue)) {
                handleAlertIgnoreAction(value, operatorInfo, requestJson);
            } else {
                log.info("未处理的卡片动作类型: {}", actionValue);
            }

        } catch (Exception e) {
            log.error("处理卡片交互失败", e);
        }
    }

    private void handleShutdownAction(JsonNode value, Map<String, String> operatorInfo, JsonNode requestJson) {
        String operatorOpenId = operatorInfo.getOrDefault("open_id", "");
        List<String> allowedIds = feishuBotProperties.getAllowedOperatorIds();
        if (!allowedIds.isEmpty() && !allowedIds.contains(operatorOpenId)) {
            log.warn("操作者无权执行关机: open_id={}", operatorOpenId);
            sendShutdownResult(operatorInfo, requestJson, null, false, "您没有权限执行此操作");
            return;
        }

        String instanceId = value.has("instance_id") ? value.get("instance_id").asText() : null;
        String providerStr = value.has("provider") ? value.get("provider").asText() : null;
        String region = value.has("region") ? value.get("region").asText() : null;

        log.info("处理关机动作: instanceId={}, provider={}, region={}, operator={}",
                instanceId, providerStr, region, operatorInfo);

        if (instanceId == null || providerStr == null) {
            log.warn("关机动作缺少必要参数");
            return;
        }

        try {
            CloudProvider provider = CloudProvider.valueOf(providerStr);
            
            Optional<CloudInstance> instanceOpt = cloudInstanceRepository
                    .findByInstanceIdAndCloudProvider(instanceId, provider);

            if (instanceOpt.isEmpty()) {
                log.warn("未找到实例: {}/{}", providerStr, instanceId);
                sendShutdownResult(operatorInfo, requestJson, null, false, "实例不存在");
                return;
            }

            CloudInstance instance = instanceOpt.get();
            
            if (!"running".equalsIgnoreCase(instance.getStatus().getCode())) {
                log.warn("实例状态不是运行中: {}/{} - {}", 
                        providerStr, instanceId, instance.getStatus());
                sendShutdownResult(operatorInfo, requestJson, instance, false, 
                        "实例状态为 " + instance.getStatus().getLabel() + "，无法关机");
                return;
            }

            String reason = "飞书卡片操作 - 操作者: " + getOperatorName(operatorInfo)
                    + " (open_id: " + operatorInfo.getOrDefault("open_id", "unknown") + ")";
            
            boolean success = instanceShutdownExecutor.shutdownInstance(instance, reason);
            
            sendShutdownResult(operatorInfo, requestJson, instance, success, 
                    success ? "关机成功" : "关机失败");

        } catch (IllegalArgumentException e) {
            log.error("无效的云厂商: {}", providerStr, e);
            sendShutdownResult(operatorInfo, requestJson, null, false, "无效的云厂商");
        } catch (Exception e) {
            log.error("处理关机动作失败", e);
            sendShutdownResult(operatorInfo, requestJson, null, false, "处理失败: " + e.getMessage());
        }
    }

    private void sendShutdownResult(Map<String, String> operatorInfo, JsonNode requestJson,
                                    CloudInstance instance, boolean success, String message) {
        try {
            String openId = operatorInfo.get("open_id");
            String cardToken = null;
            
            JsonNode event = requestJson.has("event") ? requestJson.get("event") : null;
            if (event != null && event.has("token")) {
                cardToken = event.get("token").asText();
            }

            if (instance != null) {
                ObjectNode resultCard = instanceCardBuilder.buildShutdownResultCard(instance, success, message);
                
                if (cardToken != null) {
                    feishuApiService.updateCard(cardToken, resultCard);
                } else if (openId != null) {
                    feishuApiService.sendCardMessage("open_id", openId, resultCard);
                }
            } else {
                String textMessage = (success ? "✅ " : "❌ ") + message;
                if (openId != null) {
                    feishuApiService.sendTextMessage("open_id", openId, textMessage);
                }
            }

        } catch (Exception e) {
            log.error("发送关机结果失败", e);
        }
    }

    private Map<String, String> getOperatorInfo(JsonNode requestJson) {
        Map<String, String> info = new HashMap<>();
        
        try {
            JsonNode event = requestJson.has("event") ? requestJson.get("event") : null;
            if (event != null) {
                if (event.has("operator")) {
                    JsonNode operator = event.get("operator");
                    if (operator.has("open_id")) {
                        info.put("open_id", operator.get("open_id").asText());
                    }
                    if (operator.has("user_id")) {
                        info.put("user_id", operator.get("user_id").asText());
                    }
                    if (operator.has("name")) {
                        info.put("name", operator.get("name").asText());
                    }
                }
                
                if (event.has("operator_id")) {
                    info.put("operator_id", event.get("operator_id").asText());
                }
            }
        } catch (Exception e) {
            log.warn("解析操作者信息失败", e);
        }
        
        return info;
    }

    private String getOperatorName(Map<String, String> operatorInfo) {
        if (operatorInfo.containsKey("name")) {
            return operatorInfo.get("name");
        }
        if (operatorInfo.containsKey("operator_id")) {
            return operatorInfo.get("operator_id");
        }
        if (operatorInfo.containsKey("open_id")) {
            return operatorInfo.get("open_id");
        }
        return "unknown";
    }

    private void handleAlertRestartAction(JsonNode value, Map<String, String> operatorInfo, JsonNode requestJson) {
        String operatorOpenId = operatorInfo.getOrDefault("open_id", "");
        List<String> allowedIds = feishuBotProperties.getAllowedOperatorIds();
        if (!allowedIds.isEmpty() && !allowedIds.contains(operatorOpenId)) {
            log.warn("操作者无权执行重启: open_id={}", operatorOpenId);
            sendAlertActionResult(operatorInfo, requestJson, null, false, "您没有权限执行此操作");
            return;
        }

        String instanceId = value.has("instance_id") ? value.get("instance_id").asText() : null;
        String providerStr = value.has("provider") ? value.get("provider").asText() : null;
        String region = value.has("region") ? value.get("region").asText() : null;
        String alertTypeStr = value.has("alert_type") ? value.get("alert_type").asText() : null;

        log.info("处理告警重启动作: instanceId={}, provider={}, region={}, alertType={}, operator={}",
                instanceId, providerStr, region, alertTypeStr, operatorInfo);

        if (instanceId == null || providerStr == null) {
            log.warn("重启动作缺少必要参数");
            return;
        }

        try {
            CloudProvider provider = CloudProvider.valueOf(providerStr);
            AlertType alertType = alertTypeStr != null ? AlertType.valueOf(alertTypeStr) : AlertType.CRITICAL_ERROR;
            
            Optional<CloudInstance> instanceOpt = cloudInstanceRepository
                    .findByInstanceIdAndCloudProvider(instanceId, provider);

            if (instanceOpt.isEmpty()) {
                log.warn("未找到实例: {}/{}", providerStr, instanceId);
                sendAlertActionResult(operatorInfo, requestJson, null, false, "实例不存在");
                return;
            }

            CloudInstance instance = instanceOpt.get();
            
            String reason = "飞书告警卡片重启操作 - 操作者: " + getOperatorName(operatorInfo)
                    + " (open_id: " + operatorInfo.getOrDefault("open_id", "unknown") + ")";
            
            sendAlertRestartingCard(operatorInfo, requestJson, instance, alertType);
            
            CloudProviderStrategy strategy = strategyFactory.getStrategy(provider);
            boolean success = strategy.restartInstance(instanceId, region);
            
            if (success) {
                InstanceStatus oldStatus = instance.getStatus();
                instance.restart();
                cloudInstanceRepository.save(instance);
                publishStatusChangedEvent(instance, oldStatus, InstanceStatus.RESTARTING, reason);
            }
            
            sendAlertActionResult(operatorInfo, requestJson, instance, success, 
                    success ? "重启指令已发送，实例正在重启中" : "重启失败");

        } catch (IllegalArgumentException e) {
            log.error("无效的参数: provider={}, alertType={}", providerStr, alertTypeStr, e);
            sendAlertActionResult(operatorInfo, requestJson, null, false, "无效的参数");
        } catch (Exception e) {
            log.error("处理重启动作失败", e);
            sendAlertActionResult(operatorInfo, requestJson, null, false, "处理失败: " + e.getMessage());
        }
    }

    private void handleAlertIgnoreAction(JsonNode value, Map<String, String> operatorInfo, JsonNode requestJson) {
        String operatorOpenId = operatorInfo.getOrDefault("open_id", "");
        List<String> allowedIds = feishuBotProperties.getAllowedOperatorIds();
        if (!allowedIds.isEmpty() && !allowedIds.contains(operatorOpenId)) {
            log.warn("操作者无权忽略告警: open_id={}", operatorOpenId);
            return;
        }

        String instanceId = value.has("instance_id") ? value.get("instance_id").asText() : null;
        String providerStr = value.has("provider") ? value.get("provider").asText() : null;
        String region = value.has("region") ? value.get("region").asText() : null;
        String alertTypeStr = value.has("alert_type") ? value.get("alert_type").asText() : null;

        log.info("处理告警忽略动作: instanceId={}, provider={}, region={}, alertType={}, operator={}",
                instanceId, providerStr, region, alertTypeStr, operatorInfo);

        try {
            AlertType alertType = alertTypeStr != null ? AlertType.valueOf(alertTypeStr) : AlertType.CRITICAL_ERROR;
            
            CloudProvider provider = null;
            CloudInstance instance = null;
            
            if (providerStr != null) {
                try {
                    provider = CloudProvider.valueOf(providerStr);
                    if (instanceId != null) {
                        Optional<CloudInstance> instanceOpt = cloudInstanceRepository
                                .findByInstanceIdAndCloudProvider(instanceId, provider);
                        instance = instanceOpt.orElse(null);
                    }
                } catch (IllegalArgumentException e) {
                    log.warn("无效的云厂商: {}", providerStr);
                }
            }
            
            ServerCriticalAlertEvent event = new ServerCriticalAlertEvent(
                    instanceId != null ? instanceId : "unknown",
                    provider != null ? provider : CloudProvider.ALIYUN,
                    instance != null && instance.getInstanceName() != null ? instance.getInstanceName() : "unknown",
                    region != null ? region : "unknown",
                    instance != null ? instance.getStatus() : InstanceStatus.ERROR,
                    alertType,
                    "手动忽略告警",
                    null
            );
            
            sendAlertIgnoredCard(operatorInfo, requestJson, event);
            
            log.info("告警已忽略: instanceId={}, operator={}", instanceId, getOperatorName(operatorInfo));

        } catch (Exception e) {
            log.error("处理忽略动作失败", e);
        }
    }

    private void sendAlertRestartingCard(Map<String, String> operatorInfo, JsonNode requestJson,
                                          CloudInstance instance, AlertType alertType) {
        try {
            String openId = operatorInfo.get("open_id");
            String cardToken = getCardToken(requestJson);
            
            ServerCriticalAlertEvent event = new ServerCriticalAlertEvent(
                    instance.getInstanceId(),
                    instance.getCloudProvider(),
                    instance.getInstanceName(),
                    instance.getRegion(),
                    instance.getStatus(),
                    alertType,
                    "重启中",
                    null
            );
            
            ObjectNode restartingCard = alertCardBuilder.buildRestartingAlertCard(event);
            
            if (cardToken != null) {
                feishuApiService.updateCard(cardToken, restartingCard);
            } else if (openId != null) {
                feishuApiService.sendCardMessage("open_id", openId, restartingCard);
            }

        } catch (Exception e) {
            log.error("发送重启中卡片失败", e);
        }
    }

    private void sendAlertIgnoredCard(Map<String, String> operatorInfo, JsonNode requestJson,
                                        ServerCriticalAlertEvent event) {
        try {
            String openId = operatorInfo.get("open_id");
            String cardToken = getCardToken(requestJson);
            
            ObjectNode ignoredCard = alertCardBuilder.buildIgnoredAlertCard(event);
            
            if (cardToken != null) {
                feishuApiService.updateCard(cardToken, ignoredCard);
            } else if (openId != null) {
                feishuApiService.sendCardMessage("open_id", openId, ignoredCard);
            }

        } catch (Exception e) {
            log.error("发送忽略卡片失败", e);
        }
    }

    private void sendAlertActionResult(Map<String, String> operatorInfo, JsonNode requestJson,
                                        CloudInstance instance, boolean success, String message) {
        try {
            String openId = operatorInfo.get("open_id");
            String cardToken = getCardToken(requestJson);
            
            if (instance != null) {
                ServerCriticalAlertEvent event = new ServerCriticalAlertEvent(
                        instance.getInstanceId(),
                        instance.getCloudProvider(),
                        instance.getInstanceName(),
                        instance.getRegion(),
                        instance.getStatus(),
                        AlertType.CRITICAL_ERROR,
                        message,
                        null
                );
                
                ObjectNode resultCard = alertCardBuilder.buildRestartResultCard(event, success, message);
                
                if (cardToken != null) {
                    feishuApiService.updateCard(cardToken, resultCard);
                } else if (openId != null) {
                    feishuApiService.sendCardMessage("open_id", openId, resultCard);
                }
            } else {
                String textMessage = (success ? "✅ " : "❌ ") + message;
                if (openId != null) {
                    feishuApiService.sendTextMessage("open_id", openId, textMessage);
                }
            }

        } catch (Exception e) {
            log.error("发送告警操作结果失败", e);
        }
    }

    private String getCardToken(JsonNode requestJson) {
        JsonNode event = requestJson.has("event") ? requestJson.get("event") : null;
        if (event != null && event.has("token")) {
            return event.get("token").asText();
        }
        return null;
    }

    private void publishStatusChangedEvent(CloudInstance instance,
                                           InstanceStatus oldStatus,
                                           InstanceStatus newStatus,
                                           String reason) {
        InstanceStatusChangedEvent event = new InstanceStatusChangedEvent(
                instance.getInstanceId(),
                instance.getCloudProvider(),
                instance.getInstanceName(),
                instance.getRegion(),
                oldStatus,
                newStatus
        );
        eventPublisher.publishEvent(event);
        log.debug("已发布实例状态变更事件 (告警操作): {}/{} - {} -> {}, 原因: {}",
                instance.getCloudProvider().getLabel(),
                instance.getInstanceId(),
                oldStatus,
                newStatus,
                reason);
    }
}
