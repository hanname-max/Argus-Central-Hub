package com.argus.centralhub.feishu;

import com.argus.centralhub.config.FeishuBotProperties;
import com.argus.centralhub.feishu.card.CardActionHandler;
import com.argus.centralhub.feishu.command.FeishuCommandRouter;
import com.argus.centralhub.feishu.dto.MessageContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/feishu/event")
public class FeishuEventController {

    private static final Logger log = LoggerFactory.getLogger(FeishuEventController.class);

    private final FeishuBotProperties feishuBotProperties;
    private final ObjectMapper objectMapper;
    private final FeishuCommandRouter feishuCommandRouter;
    private final CardActionHandler cardActionHandler;

    public FeishuEventController(FeishuBotProperties feishuBotProperties,
                                  ObjectMapper objectMapper,
                                  FeishuCommandRouter feishuCommandRouter,
                                  CardActionHandler cardActionHandler) {
        this.feishuBotProperties = feishuBotProperties;
        this.objectMapper = objectMapper;
        this.feishuCommandRouter = feishuCommandRouter;
        this.cardActionHandler = cardActionHandler;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> handleEvent(
            @RequestBody String body,
            @RequestHeader(value = "X-Lark-Request-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Lark-Request-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-Lark-Signature", required = false) String signature,
            @RequestHeader(value = "X-Fs-Request-Timestamp", required = false) String fsTimestamp,
            @RequestHeader(value = "X-Fs-Request-Nonce", required = false) String fsNonce,
            @RequestHeader(value = "X-Fs-Signature", required = false) String fsSignature) {

        try {
            JsonNode requestJson = objectMapper.readTree(body);

            if (!verifyRequestSecurity(timestamp, nonce, signature, fsTimestamp, fsNonce, fsSignature, body)) {
                log.warn("Security verification failed for Feishu event");
                return ResponseEntity.status(403).build();
            }

            if (requestJson.has("encrypt")) {
                String encryptData = requestJson.get("encrypt").asText();
                String decryptedBody = FeishuSecurityUtils.decrypt(feishuBotProperties.getEncryptKey(), encryptData);
                requestJson = objectMapper.readTree(decryptedBody);
            }

            if (!verifyToken(requestJson)) {
                log.warn("Token verification failed for Feishu event");
                return ResponseEntity.status(403).build();
            }

            if (!verifyTenant(requestJson)) {
                log.warn("Tenant verification failed for Feishu event");
                return ResponseEntity.status(403).build();
            }

            String type = requestJson.has("type") ? requestJson.get("type").asText() : null;
            if ("url_verification".equals(type)) {
                return handleUrlVerification(requestJson);
            }

            return handleEventCallback(requestJson);

        } catch (Exception e) {
            log.error("Failed to handle Feishu event", e);
            return ResponseEntity.status(500).build();
        }
    }

    private boolean verifyRequestSecurity(String timestamp, String nonce, String signature,
                                          String fsTimestamp, String fsNonce, String fsSignature,
                                          String body) {
        String encryptKey = feishuBotProperties.getEncryptKey();
        if (encryptKey == null || encryptKey.isEmpty()) {
            return true;
        }

        String actualTimestamp = timestamp != null ? timestamp : fsTimestamp;
        String actualNonce = nonce != null ? nonce : fsNonce;
        String actualSignature = signature != null ? signature : fsSignature;

        if (actualTimestamp == null || actualNonce == null || actualSignature == null) {
            log.warn("Missing security headers");
            return false;
        }

        try {
            long timestampValue = Long.parseLong(actualTimestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            if (Math.abs(currentTime - timestampValue) > 300) {
                log.warn("Request timestamp expired");
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid timestamp format");
            return false;
        }

        return FeishuSecurityUtils.verifySignature(actualTimestamp, actualNonce, encryptKey, body, actualSignature);
    }

    private boolean verifyToken(JsonNode requestJson) {
        String expectedToken = feishuBotProperties.getVerificationToken();
        if (expectedToken == null || expectedToken.isEmpty()) {
            return true;
        }

        String requestToken = null;
        if (requestJson.has("token")) {
            requestToken = requestJson.get("token").asText();
        } else if (requestJson.has("header") && requestJson.get("header").has("token")) {
            requestToken = requestJson.get("header").get("token").asText();
        }

        if (requestToken == null) {
            return false;
        }

        return expectedToken.equals(requestToken);
    }

    private boolean verifyTenant(JsonNode requestJson) {
        String expectedTenantKey = feishuBotProperties.getTenantKey();
        if (expectedTenantKey == null || expectedTenantKey.isEmpty()) {
            return true;
        }

        String requestTenantKey = null;
        if (requestJson.has("tenant_key")) {
            requestTenantKey = requestJson.get("tenant_key").asText();
        } else if (requestJson.has("event") && requestJson.get("event").has("tenant_key")) {
            requestTenantKey = requestJson.get("event").get("tenant_key").asText();
        } else if (requestJson.has("header") && requestJson.get("header").has("tenant_key")) {
            requestTenantKey = requestJson.get("header").get("tenant_key").asText();
        }

        if (requestTenantKey == null) {
            return true;
        }

        return expectedTenantKey.equals(requestTenantKey);
    }

    private ResponseEntity<Map<String, Object>> handleUrlVerification(JsonNode requestJson) {
        String challenge = requestJson.get("challenge").asText();
        log.info("Received URL verification request, challenge: {}", challenge);

        Map<String, Object> response = new HashMap<>();
        response.put("challenge", challenge);

        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> handleEventCallback(JsonNode requestJson) {
        String eventType = null;
        if (requestJson.has("header") && requestJson.get("header").has("event_type")) {
            eventType = requestJson.get("header").get("event_type").asText();
        } else if (requestJson.has("event") && requestJson.get("event").has("type")) {
            eventType = requestJson.get("event").get("type").asText();
        }

        log.info("Received Feishu event callback, type: {}", eventType);

        if (eventType == null) {
            log.debug("Unknown event type");
            return okResponse();
        }

        switch (eventType) {
            case "im.message.receive_v1" -> handleMessageReceive(requestJson);
            case "p2p_chat_create" -> handleP2PChatCreate(requestJson);
            case "app_mention" -> handleAppMention(requestJson);
            case "message_read" -> handleMessageRead(requestJson);
            case "card.action.trigger" -> handleCardAction(requestJson);
            default -> log.debug("Unhandled event type: {}", eventType);
        }

        return okResponse();
    }

    private void handleMessageReceive(JsonNode requestJson) {
        log.info("Handling message receive event");
        
        MessageContext context = parseMessageContext(requestJson);
        if (context != null) {
            log.info("Parsed message context: chatId={}, openId={}, messageType={}, content={}",
                    context.getChatId(), context.getOpenId(), context.getMessageType(), 
                    context.getContent() != null ? context.getContent().substring(0, Math.min(50, context.getContent().length())) : "null");
            feishuCommandRouter.routeMessage(context);
        }
    }

    private void handleAppMention(JsonNode requestJson) {
        log.info("Handling app mention event");
        
        MessageContext context = parseMessageContext(requestJson);
        if (context != null) {
            log.info("Parsed app mention context: chatId={}, openId={}", 
                    context.getChatId(), context.getOpenId());
            feishuCommandRouter.routeMessage(context);
        }
    }

    private void handleP2PChatCreate(JsonNode requestJson) {
        log.info("Handling P2P chat create event: {}", requestJson.toString());
    }

    private void handleMessageRead(JsonNode requestJson) {
        log.info("Handling message read event: {}", requestJson.toString());
    }

    private void handleCardAction(JsonNode requestJson) {
        log.info("Handling card action event");
        
        try {
            JsonNode event = requestJson.has("event") ? requestJson.get("event") : null;
            if (event == null) {
                log.warn("Card action event missing 'event' field");
                return;
            }

            JsonNode action = event.has("action") ? event.get("action") : null;
            if (action == null) {
                log.warn("Card action event missing 'action' field");
                return;
            }

            log.info("Card action: {}", action.toString());
            cardActionHandler.handleAction(action, requestJson);

        } catch (Exception e) {
            log.error("Failed to handle card action", e);
        }
    }

    private MessageContext parseMessageContext(JsonNode requestJson) {
        try {
            JsonNode event = requestJson.has("event") ? requestJson.get("event") : null;
            if (event == null) {
                log.warn("Event field not found in request");
                return null;
            }

            JsonNode message = event.has("message") ? event.get("message") : null;
            if (message == null) {
                log.warn("Message field not found in event");
                return null;
            }

            MessageContext.MessageContextBuilder builder = MessageContext.builder();

            if (message.has("chat_id")) {
                builder.chatId(message.get("chat_id").asText());
            }

            if (message.has("message_id")) {
                builder.messageId(message.get("message_id").asText());
            }

            if (message.has("msg_type")) {
                builder.messageType(message.get("msg_type").asText());
            }

            if (message.has("content")) {
                builder.content(message.get("content").asText());
            }

            JsonNode sender = event.has("sender") ? event.get("sender") : null;
            if (sender != null && sender.has("sender_id")) {
                JsonNode senderId = sender.get("sender_id");
                if (senderId.has("open_id")) {
                    builder.openId(senderId.get("open_id").asText());
                }
                if (senderId.has("user_id")) {
                    builder.userId(senderId.get("user_id").asText());
                }
            }

            String chatId = message.has("chat_id") ? message.get("chat_id").asText() : null;
            boolean isGroupChat = chatId != null && chatId.startsWith("oc_");
            builder.isGroupChat(isGroupChat);

            return builder.build();

        } catch (Exception e) {
            log.error("Failed to parse message context", e);
            return null;
        }
    }

    private ResponseEntity<Map<String, Object>> okResponse() {
        Map<String, Object> response = new HashMap<>();
        return ResponseEntity.ok(response);
    }
}
