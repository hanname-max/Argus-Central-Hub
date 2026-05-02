package com.argus.centralhub.llm;

import com.argus.centralhub.llm.LlmException.LlmErrorType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.*;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;

@Service
public class LlmService {

    private static final Logger log = LoggerFactory.getLogger(LlmService.class);

    private final LlmProperties properties;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExponentialBackoffRetryStrategy retryStrategy;

    public LlmService(LlmProperties properties, 
                      @Qualifier("llmRestTemplate") RestTemplate restTemplate, 
                      ObjectMapper objectMapper) {
        this.properties = properties;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
        this.retryStrategy = ExponentialBackoffRetryStrategy.builder()
                .maxAttempts(properties.getRetry().getMaxAttempts())
                .initialDelayMs(properties.getRetry().getInitialDelayMs())
                .multiplier(properties.getRetry().getMultiplier())
                .maxDelayMs(properties.getRetry().getMaxDelayMs())
                .build();
    }

    public String analyzeLog(String systemPrompt, String logContent) {
        if (!properties.isEnabled()) {
            log.warn("LLM 服务未启用，请配置 llm.enabled=true");
            throw new LlmException(
                    LlmErrorType.CONFIGURATION_ERROR,
                    "LLM 服务未启用"
            );
        }

        if (properties.getApiKey() == null || properties.getApiKey().isBlank()) {
            throw new LlmException(
                    LlmErrorType.CONFIGURATION_ERROR,
                    "LLM API Key 未配置"
            );
        }

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        messages.add(new ChatMessage("user", logContent));

        return chatCompletionWithRetry(messages);
    }

    public String chatCompletion(List<ChatMessage> messages) {
        if (!properties.isEnabled()) {
            log.warn("LLM 服务未启用");
            throw new LlmException(LlmErrorType.CONFIGURATION_ERROR, "LLM 服务未启用");
        }

        return chatCompletionWithRetry(messages);
    }

    private String chatCompletionWithRetry(List<ChatMessage> messages) {
        int attempt = 0;
        
        while (true) {
            try {
                return doChatCompletion(messages);
            } catch (LlmException e) {
                if (!retryStrategy.shouldRetry(attempt, e)) {
                    log.error("LLM 请求失败，已达最大重试次数或错误不可重试: {}", e.getMessage());
                    throw e;
                }
                
                attempt++;
                log.warn("LLM 请求失败 (尝试 {}/{}): {} - 将进行重试", 
                        attempt, retryStrategy.getMaxAttempts(), e.getMessage());
                
                try {
                    retryStrategy.backoff(attempt - 1);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new LlmException(
                            LlmErrorType.UNKNOWN_ERROR,
                            "重试等待被中断",
                            ie
                    );
                }
            }
        }
    }

    private String doChatCompletion(List<ChatMessage> messages) {
        try {
            String apiEndpoint = properties.getApiEndpoint();
            if (!apiEndpoint.endsWith("/")) {
                apiEndpoint += "/";
            }
            String url = apiEndpoint + "chat/completions";

            ObjectNode requestBody = buildRequestBody(messages);
            String requestBodyStr = requestBody.toString();

            log.debug("发送 LLM 请求到: {}, model: {}", url, properties.getModel());
            log.trace("请求体: {}", requestBodyStr);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(properties.getApiKey());

            HttpEntity<String> entity = new HttpEntity<>(requestBodyStr, headers);

            ResponseEntity<String> response = restTemplate.postForEntity(url, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                String responseBody = response.getBody();
                log.debug("LLM 响应成功");
                log.trace("响应体: {}", responseBody);
                
                return parseResponse(responseBody);
            } else {
                throw new LlmException(
                        LlmErrorType.fromHttpStatus(response.getStatusCode().value()),
                        response.getStatusCode().value(),
                        "LLM API 返回非成功状态码: " + response.getStatusCode(),
                        response.getBody()
                );
            }

        } catch (ResourceAccessException e) {
            return handleNetworkException(e);
        } catch (HttpClientErrorException e) {
            return handleHttpClientException(e);
        } catch (HttpServerErrorException e) {
            return handleHttpServerException(e);
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(
                    LlmErrorType.UNKNOWN_ERROR,
                    "LLM 请求发生未知错误: " + e.getMessage(),
                    e
            );
        }
    }

    private ObjectNode buildRequestBody(List<ChatMessage> messages) {
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", properties.getModel());
        body.put("temperature", properties.getTemperature());
        body.put("max_tokens", properties.getMaxTokens());

        ArrayNode messagesArray = body.putArray("messages");
        for (ChatMessage msg : messages) {
            ObjectNode messageNode = objectMapper.createObjectNode();
            messageNode.put("role", msg.role());
            messageNode.put("content", msg.content());
            messagesArray.add(messageNode);
        }

        return body;
    }

    private String parseResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode choices = root.path("choices");
            
            if (choices.isArray() && choices.size() > 0) {
                JsonNode firstChoice = choices.get(0);
                JsonNode message = firstChoice.path("message");
                JsonNode content = message.path("content");
                
                if (content != null && !content.isNull()) {
                    return content.asText();
                }
            }
            
            log.error("无法解析 LLM 响应: {}", responseBody);
            throw new LlmException(
                    LlmErrorType.PARSE_ERROR,
                    "无法解析 LLM 响应内容"
            );
        } catch (LlmException e) {
            throw e;
        } catch (Exception e) {
            throw new LlmException(
                    LlmErrorType.PARSE_ERROR,
                    "解析 LLM 响应失败: " + e.getMessage(),
                    e
            );
        }
    }

    private String handleNetworkException(ResourceAccessException e) {
        LlmErrorType errorType;
        String message;

        if (e.getCause() instanceof SocketTimeoutException) {
            errorType = LlmErrorType.NETWORK_TIMEOUT;
            message = "LLM API 连接超时: " + e.getMessage();
        } else {
            errorType = LlmErrorType.NETWORK_ERROR;
            message = "LLM API 网络错误: " + e.getMessage();
        }

        throw new LlmException(errorType, message, e);
    }

    private String handleHttpClientException(HttpClientErrorException e) {
        LlmErrorType errorType = LlmErrorType.fromHttpStatus(e.getStatusCode().value());
        String message = "LLM API 客户端错误: " + e.getStatusCode() + " - " + e.getStatusText();
        String responseBody = e.getResponseBodyAsString();

        if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
            log.warn("LLM API 限流: {}", responseBody);
        } else if (e.getStatusCode() == HttpStatus.UNAUTHORIZED) {
            log.error("LLM API 认证失败，请检查 API Key");
        }

        throw new LlmException(errorType, e.getStatusCode().value(), message, responseBody, e);
    }

    private String handleHttpServerException(HttpServerErrorException e) {
        LlmErrorType errorType = LlmErrorType.fromHttpStatus(e.getStatusCode().value());
        String message = "LLM API 服务器错误: " + e.getStatusCode() + " - " + e.getStatusText();
        String responseBody = e.getResponseBodyAsString();

        throw new LlmException(errorType, e.getStatusCode().value(), message, responseBody, e);
    }

    public record ChatMessage(String role, String content) {
        public static ChatMessage system(String content) {
            return new ChatMessage("system", content);
        }

        public static ChatMessage user(String content) {
            return new ChatMessage("user", content);
        }

        public static ChatMessage assistant(String content) {
            return new ChatMessage("assistant", content);
        }
    }
}
