package com.argus.centralhub.webhook;

import com.argus.centralhub.common.ResultData;
import com.argus.centralhub.webhook.DockerDeploymentService.DeploymentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/webhook")
public class WebhookController {

    private static final Logger log = LoggerFactory.getLogger(WebhookController.class);

    private final WebhookProperties webhookProperties;
    private final DockerDeploymentService dockerDeploymentService;
    private final ObjectMapper objectMapper;

    public WebhookController(WebhookProperties webhookProperties,
                              DockerDeploymentService dockerDeploymentService,
                              ObjectMapper objectMapper) {
        this.webhookProperties = webhookProperties;
        this.dockerDeploymentService = dockerDeploymentService;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/deploy")
    public ResponseEntity<ResultData<Map<String, Object>>> handleDeployWebhook(
            HttpServletRequest request,
            @RequestHeader Map<String, String> headers) {

        try {
            if (!webhookProperties.isEnabled()) {
                log.warn("Webhook 功能未启用");
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(ResultData.failed(503, "Webhook 功能未启用"));
            }

            String payload = readPayload(request);
            if (payload == null || payload.isEmpty()) {
                log.warn("空的请求体");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResultData.failed(400, "空的请求体"));
            }

            if (payload.length() > webhookProperties.getMaxPayloadSize()) {
                log.warn("请求体过大: {} bytes", payload.length());
                return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResultData.failed(400, "请求体过大，最大允许: " + webhookProperties.getMaxPayloadSize() + " bytes"));
            }

            if (!verifySignature(payload, headers)) {
                log.warn("签名验证失败");
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ResultData.failed(403, "签名验证失败"));
            }

            if (!verifyIpAddress(request)) {
                log.warn("IP 地址不在白名单中: {}", getClientIp(request));
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(ResultData.failed(403, "IP 地址不在白名单中"));
            }

            JsonNode requestJson = objectMapper.readTree(payload);
            
            String action = requestJson.has("action") ? requestJson.get("action").asText() : "deploy";
            log.info("收到 Webhook 部署请求，action: {}", action);

            return switch (action) {
                case "deploy" -> handleDeployAction(requestJson);
                case "pull" -> handlePullAction(requestJson);
                case "restart" -> handleRestartAction(requestJson);
                default -> ResponseEntity.status(HttpStatus.BAD_REQUEST)
                        .body(ResultData.failed(400, "不支持的 action: " + action));
            };

        } catch (Exception e) {
            log.error("处理 Webhook 请求失败", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResultData.failed(500, "处理 Webhook 请求失败: " + e.getMessage()));
        }
    }

    private ResponseEntity<ResultData<Map<String, Object>>> handleDeployAction(JsonNode requestJson) {
        String nodeName = getRequiredField(requestJson, "node_name", "Docker 节点名称");
        String imageName = getRequiredField(requestJson, "image", "镜像名称");
        String containerName = getRequiredField(requestJson, "container_name", "容器名称");

        if (nodeName == null || imageName == null || containerName == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResultData.failed(400, "缺少必要参数: node_name, image, container_name"));
        }

        WebhookProperties.RegistryAuthConfig registryAuth = webhookProperties.findRegistryAuthForImage(imageName);
        String registryUsername = registryAuth != null ? registryAuth.getUsername() : null;
        String registryPassword = registryAuth != null ? registryAuth.getPassword() : null;

        log.info("执行部署操作: node={}, image={}, container={}", nodeName, imageName, containerName);

        DeploymentResult result = dockerDeploymentService.deployService(
                nodeName, imageName, containerName, registryUsername, registryPassword);

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        data.put("node_name", nodeName);
        data.put("image", imageName);
        data.put("container_name", containerName);
        data.put("success", result.success());
        data.put("message", result.message());

        if (result.success()) {
            log.info("部署成功: node={}, container={}", nodeName, containerName);
            return ResponseEntity.ok(ResultData.success("部署成功", data));
        } else {
            log.error("部署失败: node={}, container={}, message={}", nodeName, containerName, result.message());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResultData.failed(500, "部署失败: " + result.message()));
        }
    }

    private ResponseEntity<ResultData<Map<String, Object>>> handlePullAction(JsonNode requestJson) {
        String nodeName = getRequiredField(requestJson, "node_name", "Docker 节点名称");
        String imageName = getRequiredField(requestJson, "image", "镜像名称");

        if (nodeName == null || imageName == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResultData.failed(400, "缺少必要参数: node_name, image"));
        }

        WebhookProperties.RegistryAuthConfig registryAuth = webhookProperties.findRegistryAuthForImage(imageName);
        String registryUsername = registryAuth != null ? registryAuth.getUsername() : null;
        String registryPassword = registryAuth != null ? registryAuth.getPassword() : null;

        log.info("执行拉取镜像操作: node={}, image={}", nodeName, imageName);

        boolean success = dockerDeploymentService.pullImage(
                nodeName, imageName, registryUsername, registryPassword);

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        data.put("node_name", nodeName);
        data.put("image", imageName);
        data.put("success", success);

        if (success) {
            log.info("镜像拉取成功: node={}, image={}", nodeName, imageName);
            return ResponseEntity.ok(ResultData.success("镜像拉取成功", data));
        } else {
            log.error("镜像拉取失败: node={}, image={}", nodeName, imageName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResultData.failed(500, "镜像拉取失败"));
        }
    }

    private ResponseEntity<ResultData<Map<String, Object>>> handleRestartAction(JsonNode requestJson) {
        String nodeName = getRequiredField(requestJson, "node_name", "Docker 节点名称");
        String containerName = getRequiredField(requestJson, "container_name", "容器名称");

        if (nodeName == null || containerName == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ResultData.failed(400, "缺少必要参数: node_name, container_name"));
        }

        log.info("执行重启容器操作: node={}, container={}", nodeName, containerName);

        boolean success = dockerDeploymentService.restartContainer(nodeName, containerName);

        Map<String, Object> data = new HashMap<>();
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        data.put("node_name", nodeName);
        data.put("container_name", containerName);
        data.put("success", success);

        if (success) {
            log.info("容器重启成功: node={}, container={}", nodeName, containerName);
            return ResponseEntity.ok(ResultData.success("容器重启成功", data));
        } else {
            log.error("容器重启失败: node={}, container={}", nodeName, containerName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ResultData.failed(500, "容器重启失败"));
        }
    }

    private String getRequiredField(JsonNode json, String fieldName, String description) {
        if (json.has(fieldName)) {
            String value = json.get(fieldName).asText();
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        log.warn("缺少必要字段: {} ({})", fieldName, description);
        return null;
    }

    private boolean verifySignature(String payload, Map<String, String> headers) {
        String secret = webhookProperties.getSecret();
        if (secret == null || secret.isEmpty()) {
            log.warn("Webhook secret 未配置，跳过签名验证");
            return true;
        }

        String signatureHeader = webhookProperties.getSignatureHeader();
        String signature = null;
        
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(signatureHeader)) {
                signature = entry.getValue();
                break;
            }
        }

        if (signature == null) {
            log.warn("缺少签名头部: {}", signatureHeader);
            return false;
        }

        String prefix = webhookProperties.getSignaturePrefix();
        if (prefix != null && !prefix.isEmpty()) {
            return WebhookSecurityUtils.verifyHmacSha256WithPrefix(secret, payload, signature, prefix);
        } else {
            return WebhookSecurityUtils.verifyHmacSha256(secret, payload, signature);
        }
    }

    private boolean verifyIpAddress(HttpServletRequest request) {
        var allowedIps = webhookProperties.getAllowedIps();
        if (allowedIps == null || allowedIps.isEmpty()) {
            return true;
        }

        String clientIp = getClientIp(request);
        return allowedIps.contains(clientIp);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        
        return ip;
    }

    private String readPayload(HttpServletRequest request) throws IOException {
        try (InputStream inputStream = request.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            StringBuilder stringBuilder = new StringBuilder();
            char[] buffer = new char[1024];
            int bytesRead;
            while ((bytesRead = reader.read(buffer)) != -1) {
                stringBuilder.append(buffer, 0, bytesRead);
            }
            return stringBuilder.toString();
        }
    }

    @GetMapping("/health")
    public ResponseEntity<ResultData<Map<String, Object>>> healthCheck() {
        Map<String, Object> data = new HashMap<>();
        data.put("status", "ok");
        data.put("enabled", webhookProperties.isEnabled());
        data.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return ResponseEntity.ok(ResultData.success(data));
    }
}
