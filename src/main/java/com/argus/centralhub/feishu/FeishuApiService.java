package com.argus.centralhub.feishu;

import com.argus.centralhub.circuitbreaker.annotation.CircuitBreakerProtect;
import com.argus.centralhub.config.FeishuBotProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class FeishuApiService {

    private static final Logger log = LoggerFactory.getLogger(FeishuApiService.class);
    
    private static final String FEISHU_API_BASE = "https://open.feishu.cn/open-apis";
    private static final String TENANT_ACCESS_TOKEN_URL = FEISHU_API_BASE + "/auth/v3/tenant_access_token/internal";
    private static final String SEND_MESSAGE_URL = FEISHU_API_BASE + "/im/v1/messages?receive_id_type=%s";
    private static final String REPLY_MESSAGE_URL = FEISHU_API_BASE + "/im/v1/messages/%s/reply";
    private static final String UPDATE_CARD_URL = FEISHU_API_BASE + "/card/v3.0/card/update";
    
    private final FeishuBotProperties feishuBotProperties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    
    private volatile String cachedAccessToken;
    private volatile long tokenExpireTime;
    private final ReentrantLock tokenLock = new ReentrantLock();

    public FeishuApiService(FeishuBotProperties feishuBotProperties, 
                            RestTemplate restTemplate, 
                            ObjectMapper objectMapper) {
        this.feishuBotProperties = feishuBotProperties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public String getTenantAccessToken() {
        long now = Instant.now().getEpochSecond();
        
        if (cachedAccessToken != null && now < tokenExpireTime - 60) {
            return cachedAccessToken;
        }
        
        tokenLock.lock();
        try {
            if (cachedAccessToken != null && now < tokenExpireTime - 60) {
                return cachedAccessToken;
            }
            
            return refreshAccessToken();
        } finally {
            tokenLock.unlock();
        }
    }

    @CircuitBreakerProtect(
            name = "feishu-api-refreshToken",
            timeoutMs = 10000,
            maxRetries = 2,
            fallbackMethod = "refreshTokenFallback"
    )
    private String refreshAccessToken() {
        try {
            Map<String, String> requestBody = new HashMap<>();
            requestBody.put("app_id", feishuBotProperties.getAppId());
            requestBody.put("app_secret", feishuBotProperties.getAppSecret());
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            
            HttpEntity<Map<String, String>> entity = new HttpEntity<>(requestBody, headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(
                    TENANT_ACCESS_TOKEN_URL, entity, String.class);
            
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            
            int code = responseJson.get("code").asInt();
            if (code != 0) {
                log.error("获取 tenant_access_token 失败: {}", responseJson.get("msg").asText());
                throw new RuntimeException("获取 tenant_access_token 失败");
            }
            
            this.cachedAccessToken = responseJson.get("tenant_access_token").asText();
            this.tokenExpireTime = Instant.now().getEpochSecond() + responseJson.get("expire").asInt();
            
            log.info("成功获取新的 tenant_access_token，有效期: {} 秒", responseJson.get("expire").asInt());
            
            return this.cachedAccessToken;
            
        } catch (Exception e) {
            log.error("刷新 Access Token 失败", e);
            throw new RuntimeException("刷新 Access Token 失败", e);
        }
    }

    private String refreshTokenFallback(Throwable cause) {
        log.warn("飞书 API refreshToken 断路器触发，尝试使用缓存的 token。原因: {}", cause.getMessage());
        if (cachedAccessToken != null) {
            log.warn("使用缓存的 access_token（可能已过期）");
            return cachedAccessToken;
        }
        throw new RuntimeException("飞书 Token 服务不可用，且无缓存可用", cause);
    }

    @CircuitBreakerProtect(
            name = "feishu-api-sendMessage",
            timeoutMs = 10000,
            maxRetries = 2,
            fallbackMethod = "sendMessageFallback"
    )
    public boolean sendTextMessage(String receiveIdType, String receiveId, String text) {
        try {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("text", text);
            
            return sendMessage(receiveIdType, receiveId, "text", content.toString());
        } catch (Exception e) {
            log.error("发送文本消息失败", e);
            return false;
        }
    }

    @CircuitBreakerProtect(
            name = "feishu-api-sendMessage",
            timeoutMs = 10000,
            maxRetries = 2,
            fallbackMethod = "sendMessageFallback"
    )
    public boolean sendCardMessage(String receiveIdType, String receiveId, ObjectNode cardContent) {
        try {
            return sendMessage(receiveIdType, receiveId, "interactive", cardContent.toString());
        } catch (Exception e) {
            log.error("发送卡片消息失败", e);
            return false;
        }
    }

    private boolean sendMessageFallback(String receiveIdType, String receiveId, Object content, Throwable cause) {
        log.warn("飞书 API sendMessage 断路器触发，消息发送失败。接收者: {}, 原因: {}", receiveId, cause.getMessage());
        return false;
    }

    @CircuitBreakerProtect(
            name = "feishu-api-replyMessage",
            timeoutMs = 10000,
            maxRetries = 2,
            fallbackMethod = "replyMessageFallback"
    )
    public boolean replyTextMessage(String messageId, String text) {
        try {
            ObjectNode content = objectMapper.createObjectNode();
            content.put("text", text);
            
            return replyMessage(messageId, "text", content.toString());
        } catch (Exception e) {
            log.error("回复文本消息失败", e);
            return false;
        }
    }

    @CircuitBreakerProtect(
            name = "feishu-api-replyMessage",
            timeoutMs = 10000,
            maxRetries = 2,
            fallbackMethod = "replyMessageFallback"
    )
    public boolean replyCardMessage(String messageId, ObjectNode cardContent) {
        try {
            return replyMessage(messageId, "interactive", cardContent.toString());
        } catch (Exception e) {
            log.error("回复卡片消息失败", e);
            return false;
        }
    }

    private boolean replyMessageFallback(String messageId, Object content, Throwable cause) {
        log.warn("飞书 API replyMessage 断路器触发，消息回复失败。消息ID: {}, 原因: {}", messageId, cause.getMessage());
        return false;
    }

    private boolean sendMessage(String receiveIdType, String receiveId, String msgType, String content) {
        try {
            String url = String.format(SEND_MESSAGE_URL, receiveIdType);
            
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("receive_id", receiveId);
            requestBody.put("msg_type", msgType);
            requestBody.put("content", content);
            
            String accessToken = getTenantAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            int code = responseJson.get("code").asInt();
            
            if (code != 0) {
                log.error("发送消息失败: {} - {}", code, responseJson.get("msg").asText());
                return false;
            }
            
            log.info("消息发送成功，receive_id: {}", receiveId);
            return true;
            
        } catch (Exception e) {
            log.error("调用飞书发送消息 API 失败", e);
            return false;
        }
    }

    private boolean replyMessage(String messageId, String msgType, String content) {
        try {
            String url = String.format(REPLY_MESSAGE_URL, messageId);
            
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("msg_type", msgType);
            requestBody.put("content", content);
            
            String accessToken = getTenantAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);
            
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            int code = responseJson.get("code").asInt();
            
            if (code != 0) {
                log.error("回复消息失败: {} - {}", code, responseJson.get("msg").asText());
                return false;
            }
            
            log.info("消息回复成功，message_id: {}", messageId);
            return true;
            
        } catch (Exception e) {
            log.error("调用飞书回复消息 API 失败", e);
            return false;
        }
    }

    @CircuitBreakerProtect(
            name = "feishu-api-updateCard",
            timeoutMs = 10000,
            maxRetries = 2,
            fallbackMethod = "updateCardFallback"
    )
    public boolean updateCard(String token, ObjectNode cardContent) {
        try {
            ObjectNode requestBody = objectMapper.createObjectNode();
            requestBody.put("token", token);
            requestBody.set("card", cardContent);
            
            String accessToken = getTenantAccessToken();
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(accessToken);
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toString(), headers);
            
            ResponseEntity<String> response = restTemplate.postForEntity(UPDATE_CARD_URL, entity, String.class);
            
            JsonNode responseJson = objectMapper.readTree(response.getBody());
            int code = responseJson.get("code").asInt();
            
            if (code != 0) {
                log.error("更新卡片失败: {} - {}", code, responseJson.get("msg").asText());
                return false;
            }
            
            log.info("卡片更新成功，token: {}", token);
            return true;
            
        } catch (Exception e) {
            log.error("调用飞书更新卡片 API 失败", e);
            return false;
        }
    }

    private boolean updateCardFallback(String token, ObjectNode cardContent, Throwable cause) {
        log.warn("飞书 API updateCard 断路器触发，卡片更新失败。token: {}, 原因: {}", token, cause.getMessage());
        return false;
    }
}
